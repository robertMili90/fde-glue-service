package com.example.fde.glue.webhook.application;

import com.example.fde.glue.stripe.DecodedWebhook;
import com.example.fde.glue.stripe.StripeWebhookDecoder;
import com.example.fde.glue.work.WorkStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AcceptStripeWebhook {
  public enum Status {
    ignored,
    recorded,
    duplicate
  }

  private static final Logger log = LoggerFactory.getLogger(AcceptStripeWebhook.class);
  private final StripeWebhookDecoder decoder;
  private final WorkStore workStore;

  public AcceptStripeWebhook(StripeWebhookDecoder decoder, WorkStore workStore) {
    this.decoder = decoder;
    this.workStore = workStore;
  }

  public Status accept(String rawBody, String signature) {
    DecodedWebhook webhook = decoder.decode(rawBody, signature);
    if (webhook.ignored()) {
      return Status.ignored;
    }
    boolean inserted =
        workStore.accept(
            webhook.stripeEventId(), webhook.eventType(), webhook.paymentIntentId(), rawBody);
    log.info(
        "webhook event={} payment={} inserted={}",
        webhook.stripeEventId(),
        webhook.paymentIntentId(),
        inserted);
    return inserted ? Status.recorded : Status.duplicate;
  }
}
