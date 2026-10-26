package com.example.fde.glue.stripe;

public record DecodedWebhook(
    String stripeEventId, String eventType, String paymentIntentId, boolean ignored) {
  static DecodedWebhook ignoredEvent() {
    return new DecodedWebhook(null, null, null, true);
  }
}
