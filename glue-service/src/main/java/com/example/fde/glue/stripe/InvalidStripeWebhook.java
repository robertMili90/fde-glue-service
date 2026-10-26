package com.example.fde.glue.stripe;

public class InvalidStripeWebhook extends RuntimeException {
  private final WebhookRejection reason;

  InvalidStripeWebhook(WebhookRejection reason) {
    this(reason, null);
  }

  InvalidStripeWebhook(WebhookRejection reason, Throwable cause) {
    super(reason.name(), cause);
    this.reason = reason;
  }

  public WebhookRejection reason() {
    return reason;
  }
}
