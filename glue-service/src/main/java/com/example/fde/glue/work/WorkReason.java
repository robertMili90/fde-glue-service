package com.example.fde.glue.work;

final class WorkReason {
  static final String PAYMENT_CANCELED = "PAYMENT_CANCELED";
  static final String RESTART_RECOVERY = "RESTART_RECOVERY";
  static final String OPERATOR_REQUEUE = "OPERATOR_REQUEUE";
  static final String INTERNAL_ERROR = "INTERNAL_ERROR";
  static final String RETRY_EXHAUSTED_PREFIX = "RETRY_EXHAUSTED_";

  private WorkReason() {}
}
