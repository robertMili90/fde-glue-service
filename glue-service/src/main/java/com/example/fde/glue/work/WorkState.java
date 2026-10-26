package com.example.fde.glue.work;

public enum WorkState {
  READY,
  RUNNING,
  RETRY_SCHEDULED,
  WAITING_PROVIDER,
  DONE,
  NEEDS_REVIEW,
  FAILED;

  public boolean held() {
    return this == NEEDS_REVIEW || this == FAILED;
  }
}
