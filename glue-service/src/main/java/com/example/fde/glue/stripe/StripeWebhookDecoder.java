package com.example.fde.glue.stripe;

import com.example.fde.glue.config.StripeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class StripeWebhookDecoder {
  private static final Set<String> SUPPORTED_TYPES =
      Set.of(
          "payment_intent.succeeded",
          "payment_intent.payment_failed",
          "refund.created",
          "refund.updated",
          "refund.failed");
  private final StripeProperties credentials;
  private final ObjectMapper json;
  private final Clock clock;

  public StripeWebhookDecoder(StripeProperties credentials, ObjectMapper json, Clock clock) {
    this.credentials = credentials;
    this.json = json;
    this.clock = clock;
  }

  public DecodedWebhook decode(String rawBody, String signature) {
    if (credentials.webhookSecret() == null || credentials.webhookSecret().isEmpty()) {
      throw new InvalidStripeWebhook(WebhookRejection.WEBHOOK_NOT_CONFIGURED);
    }
    try {
      // Verify the exact request bytes represented by rawBody before parsing
      // or entering the short transaction that accepts durable work.
      Webhook.Signature.verifyHeader(
          rawBody, signature, credentials.webhookSecret(), Webhook.DEFAULT_TOLERANCE, clock);
      JsonNode envelope = json.readTree(rawBody);
      if (envelope == null) {
        throw new InvalidStripeWebhook(WebhookRejection.INVALID_WEBHOOK);
      }
      requireTestEvent(envelope);
      String eventType = envelope.path("type").asText();
      if (!SUPPORTED_TYPES.contains(eventType)) {
        return DecodedWebhook.ignoredEvent();
      }
      JsonNode resource = envelope.path("data").path("object");
      String stripeEventId = envelope.path("id").asText();
      String paymentIntentId = paymentIntentId(eventType, resource);
      if (!stripeEventId.matches("evt_\\S+")
          || stripeEventId.length() > 255
          || !paymentIntentId.matches("pi_[A-Za-z0-9_]+")
          || paymentIntentId.length() > 255) {
        throw new InvalidStripeWebhook(WebhookRejection.INVALID_EVENT_REFERENCE);
      }
      if (resource.path("livemode").asBoolean(false)) {
        throw new InvalidStripeWebhook(WebhookRejection.LIVE_RESOURCE_IN_EVENT);
      }
      return new DecodedWebhook(stripeEventId, eventType, paymentIntentId, false);
    } catch (SignatureVerificationException | IOException invalid) {
      throw new InvalidStripeWebhook(WebhookRejection.INVALID_WEBHOOK, invalid);
    }
  }

  private void requireTestEvent(JsonNode envelope) {
    JsonNode liveMode = envelope.path("livemode");
    if (!liveMode.isBoolean() || liveMode.booleanValue()) {
      throw new InvalidStripeWebhook(WebhookRejection.LIVE_OR_UNKNOWN_MODE_EVENT);
    }
  }

  private String paymentIntentId(String eventType, JsonNode resource) {
    if (eventType.startsWith("payment_intent.")) {
      return resource.path("id").asText();
    }
    JsonNode paymentIntent = resource.path("payment_intent");
    if (paymentIntent.isObject()) {
      return paymentIntent.path("id").asText();
    }
    return paymentIntent.asText();
  }
}
