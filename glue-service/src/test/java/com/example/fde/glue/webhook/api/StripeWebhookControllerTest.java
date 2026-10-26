package com.example.fde.glue.webhook.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fde.glue.config.StripeProperties;
import com.example.fde.glue.stripe.StripeWebhookDecoder;
import com.example.fde.glue.webhook.application.AcceptStripeWebhook;
import com.example.fde.glue.work.WorkStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.net.Webhook;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StripeWebhookControllerTest {
  private static final String SECRET = "whsec_offline_fixture";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"), ZoneOffset.UTC);
  private static final String PAYMENT_EVENT =
      """
      { "id": "evt_receipt", "type": "payment_intent.succeeded", "livemode": false,
        "data": {"object": {"id": "pi_receipt"}} }
      """;
  private final WorkStore workStore = mock(WorkStore.class);
  private final MockMvc http = controller(SECRET);

  @Test
  void signedRawBodyReachesAtomicAcceptanceAndReceiptShapeIsStableForDuplicates() throws Exception {
    when(workStore.accept("evt_receipt", "payment_intent.succeeded", "pi_receipt", PAYMENT_EVENT))
        .thenReturn(true, false);
    signed(http, PAYMENT_EVENT)
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"recorded\"}", JsonCompareMode.STRICT));
    signed(http, PAYMENT_EVENT)
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"duplicate\"}", JsonCompareMode.STRICT));
    verify(workStore, times(2))
        .accept("evt_receipt", "payment_intent.succeeded", "pi_receipt", PAYMENT_EVENT);
  }

  @Test
  void refundReferencesMayBeExpandedObjectsAndUnsupportedTypesDoNotReachPersistence()
      throws Exception {
    String refundEvent =
        """
        {"id":"evt_refund","type":"refund.updated","livemode":false,
         "data":{"object":{"id":"re_notice","payment_intent":{"id":"pi_refund"}}}}
        """;
    when(workStore.accept("evt_refund", "refund.updated", "pi_refund", refundEvent))
        .thenReturn(true);
    signed(http, refundEvent)
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"recorded\"}", JsonCompareMode.STRICT));
    verify(workStore).accept("evt_refund", "refund.updated", "pi_refund", refundEvent);
    signed(http, "{\"type\":\"customer.created\",\"livemode\":false}")
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"ignored\"}", JsonCompareMode.STRICT));
  }

  @Test
  void invalidSignatureAndInvalidSignedJsonHaveTheSameSafeClientResponse() throws Exception {
    http.perform(
            post("/webhooks/stripe")
                .contentType("application/json")
                .content(PAYMENT_EVENT)
                .header("Stripe-Signature", "invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .json(
                    "{\"reasonCode\":\"INVALID_WEBHOOK\",\"message\":\"Webhook signature or JSON body is invalid\"}",
                    JsonCompareMode.STRICT));
    for (String malformed : List.of("{invalid")) {
      signed(http, malformed)
          .andExpect(status().isBadRequest())
          .andExpect(
              content()
                  .json(
                      "{\"reasonCode\":\"INVALID_WEBHOOK\",\"message\":\"Webhook signature or JSON body is invalid\"}",
                      JsonCompareMode.STRICT));
    }
    signed(http, "null")
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .json(
                    "{\"reasonCode\":\"LIVE_OR_UNKNOWN_MODE_EVENT\",\"message\":\"Only explicitly test-mode events are accepted\"}",
                    JsonCompareMode.STRICT));
    verifyNoInteractions(workStore);
  }

  @Test
  void modeAndReferenceRejectionsRemainDistinctAndNeverAcceptWork() throws Exception {
    signed(http, PAYMENT_EVENT.replace("\"livemode\": false", "\"livemode\": true"))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .json(
                    "{\"reasonCode\":\"LIVE_OR_UNKNOWN_MODE_EVENT\",\"message\":\"Only explicitly test-mode events are accepted\"}",
                    JsonCompareMode.STRICT));
    signed(http, PAYMENT_EVENT.replace("\"livemode\": false,", ""))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .json(
                    "{\"reasonCode\":\"LIVE_OR_UNKNOWN_MODE_EVENT\",\"message\":\"Only explicitly test-mode events are accepted\"}",
                    JsonCompareMode.STRICT));
    signed(http, PAYMENT_EVENT.replace("\"pi_receipt\"", "\"invalid_reference\""))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .json(
                    "{\"reasonCode\":\"INVALID_EVENT_REFERENCE\",\"message\":\"Webhook event or payment reference is invalid\"}",
                    JsonCompareMode.STRICT));
    signed(
            http,
            PAYMENT_EVENT.replace(
                "\"id\": \"pi_receipt\"", "\"id\": \"pi_receipt\", \"livemode\": true"))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .json(
                    "{\"reasonCode\":\"LIVE_RESOURCE_IN_EVENT\",\"message\":\"Live-mode resources are not accepted\"}",
                    JsonCompareMode.STRICT));
    verifyNoInteractions(workStore);
  }

  @Test
  void missingConfigurationAndUnavailablePersistenceStayRetryableWithoutExposingInternals()
      throws Exception {
    signed(controller(""), PAYMENT_EVENT)
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            content()
                .json(
                    "{\"reasonCode\":\"WEBHOOK_NOT_CONFIGURED\",\"message\":\"Stripe webhook verification is not configured\"}",
                    JsonCompareMode.STRICT));
    verifyNoInteractions(workStore);
    when(workStore.accept(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new DataAccessResourceFailureException("private persistence diagnostic"));
    signed(http, PAYMENT_EVENT)
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            content()
                .json(
                    "{\"reasonCode\":\"PERSISTENCE_UNAVAILABLE\",\"message\":\"Webhook could not be persisted; retry delivery\"}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void unexpectedApplicationFailuresAreNotTranslatedIntoClientErrors() {
    when(workStore.accept(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new IllegalStateException("unexpected programming failure"));
    assertThatThrownBy(() -> signed(http, PAYMENT_EVENT))
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("unexpected programming failure");
  }

  private MockMvc controller(String webhookSecret) {
    var decoder =
        new StripeWebhookDecoder(
            new StripeProperties("", webhookSecret), new ObjectMapper(), CLOCK);
    return MockMvcBuilders.standaloneSetup(
            new StripeWebhookController(new AcceptStripeWebhook(decoder, workStore)))
        .build();
  }

  private ResultActions signed(MockMvc controller, String rawBody) throws Exception {
    String signature =
        Webhook.Signature.generateSignatureHeader(
            rawBody, SECRET, CLOCK.instant().getEpochSecond());
    return controller.perform(
        post("/webhooks/stripe")
            .contentType("application/json")
            .header("Stripe-Signature", signature)
            .content(rawBody));
  }
}
