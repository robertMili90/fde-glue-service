package com.example.fde.glue.work;

public class SyncFailure extends RuntimeException {
  private final WorkState state;
  private final String reason;
  private final long retryAfterMs;

  public SyncFailure(WorkState state, String reason) {
    this(state, reason, 0, null);
  }

  public SyncFailure(WorkState state, String reason, long retryAfterMs) {
    this(state, reason, retryAfterMs, null);
  }

  public SyncFailure(WorkState state, String reason, Throwable cause) {
    this(state, reason, 0, cause);
  }

  public SyncFailure(WorkState state, String reason, long retryAfterMs, Throwable cause) {
    super(reason, cause);
    this.state = state;
    this.reason = reason;
    this.retryAfterMs = retryAfterMs;
  }

  public WorkState state() {
    return state;
  }

  public String reason() {
    return reason;
  }

  public long retryAfterMs() {
    return retryAfterMs;
  }

  public static SyncFailure http(String source, int status, long retryAfterMs) {
    return http(source, status, retryAfterMs, null);
  }

  public static SyncFailure http(String source, int status, long retryAfterMs, Throwable cause) {
    if (status == 408 || status == 429 || status >= 500) {
      return new SyncFailure(
          WorkState.RETRY_SCHEDULED, source + "_HTTP_" + status, retryAfterMs, cause);
    }
    if (status == 401 || status == 403) {
      return new SyncFailure(WorkState.FAILED, source + "_AUTHENTICATION", cause);
    }
    return new SyncFailure(WorkState.NEEDS_REVIEW, source + "_HTTP_" + status, cause);
  }
}
