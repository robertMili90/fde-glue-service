package com.example.fde.glue.work;

public enum AttemptOutcome {
  READY,
  RUNNING,
  RETRY_SCHEDULED,
  WAITING_PROVIDER,
  DONE,
  NEEDS_REVIEW,
  FAILED,
  INTERRUPTED
}
