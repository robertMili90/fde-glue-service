package com.example.fde.glue.stripe;

public enum WebhookRejection {
  WEBHOOK_NOT_CONFIGURED,
  LIVE_OR_UNKNOWN_MODE_EVENT,
  INVALID_EVENT_REFERENCE,
  LIVE_RESOURCE_IN_EVENT,
  INVALID_WEBHOOK
}
