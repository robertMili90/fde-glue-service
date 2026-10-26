package com.example.fde.glue.work;

import com.example.fde.glue.config.SyncSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "work_item", schema = "glue")
class WorkItem implements Persistable<String> {
  // Assigned IDs must still use insert semantics for new instances.
  @Transient private boolean newEntity = true;

  @Id
  @Column(length = 255)
  private String paymentIntentId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 32)
  private WorkState state;

  private long requestedGeneration;
  private long completedGeneration;
  private long processingGeneration;
  private int transientFailures;
  private int totalAttempts;
  private Instant nextEligibleAt;

  @Column(nullable = false)
  private Instant pendingSince;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Column(length = 100)
  private String reasonCode;

  @Column(length = 255)
  private String invoiceId;

  @Column(length = 255)
  private String operationKey;

  @Column(length = 255)
  private String lastEventId;

  @Lob private String facts;

  protected WorkItem() {}

  WorkItem(String paymentIntentId, Instant now) {
    this.paymentIntentId = paymentIntentId;
    state = WorkState.READY;
    createdAt = now;
    updatedAt = now;
    pendingSince = now;
    nextEligibleAt = now;
  }

  void request(String stripeEventId, boolean coalesce, Instant now) {
    // Cancellation is terminal in Stripe. Skip scan refreshes, but preserve new
    // webhook requests and generations already received during an observation.
    if (coalesce && state == WorkState.DONE && WorkReason.PAYMENT_CANCELED.equals(reasonCode)) {
      return;
    }
    if (state == WorkState.DONE) {
      pendingSince = now;
    }
    if (!coalesce || requestedGeneration <= processingGeneration) {
      requestedGeneration++;
    }
    if (stripeEventId != null) {
      lastEventId = stripeEventId;
    }
    updatedAt = now;
    if (!state.held()
        && state != WorkState.RUNNING
        && state != WorkState.RETRY_SCHEDULED
        && state != WorkState.READY) {
      state = WorkState.READY;
      nextEligibleAt = now;
    }
  }

  WorkStore.Claim claim(String attemptId, Instant now) {
    if (state != WorkState.READY
        && state != WorkState.RETRY_SCHEDULED
        && state != WorkState.WAITING_PROVIDER) {
      throw new IllegalStateException("Work is not eligible to claim");
    }
    state = WorkState.RUNNING;
    processingGeneration = requestedGeneration;
    totalAttempts++;
    updatedAt = now;
    return new WorkStore.Claim(
        paymentIntentId, processingGeneration, attemptId, totalAttempts, lastEventId);
  }

  WorkStore.Completion complete(
      WorkStore.Claim claim, WorkStore.Outcome outcome, SyncSettings settings, Instant now) {
    if (state != WorkState.RUNNING || processingGeneration != claim.generation()) {
      throw new IllegalStateException("Work ownership changed");
    }
    reasonCode = outcome.reason();
    updatedAt = now;
    if (outcome.invoiceId() != null) {
      invoiceId = outcome.invoiceId();
    }
    if (outcome.operationKey() != null) {
      operationKey = outcome.operationKey();
    }
    if (outcome.facts() != null) {
      facts = outcome.facts();
    }
    switch (outcome.state()) {
      case RETRY_SCHEDULED -> scheduleRetry(outcome, settings, now);
      case DONE, WAITING_PROVIDER -> completeObservation(claim, outcome, settings, now);
      case NEEDS_REVIEW, FAILED -> {
        state = outcome.state();
        nextEligibleAt = null;
      }
      default -> throw new IllegalArgumentException("Invalid completion state: " + outcome.state());
    }
    return new WorkStore.Completion(AttemptOutcome.valueOf(state.name()), reasonCode);
  }

  private void scheduleRetry(WorkStore.Outcome outcome, SyncSettings settings, Instant now) {
    transientFailures++;
    if (transientFailures >= settings.maxAttempts()) {
      state = WorkState.FAILED;
      reasonCode = WorkReason.RETRY_EXHAUSTED_PREFIX + outcome.reason();
      nextEligibleAt = null;
      return;
    }
    state = WorkState.RETRY_SCHEDULED;
    long backoff =
        Math.min(settings.retryMaxMs(), settings.retryBaseMs() * (1L << (transientFailures - 1)));
    nextEligibleAt = now.plusMillis(Math.max(backoff, Math.min(60000, outcome.retryAfterMs())));
  }

  private void completeObservation(
      WorkStore.Claim claim, WorkStore.Outcome outcome, SyncSettings settings, Instant now) {
    transientFailures = 0;
    completedGeneration = claim.generation();
    if (requestedGeneration > claim.generation()) {
      state = WorkState.READY;
      nextEligibleAt = now;
      return;
    }
    state = outcome.state();
    nextEligibleAt =
        state == WorkState.WAITING_PROVIDER ? now.plusMillis(settings.providerPollMs()) : null;
  }

  void requeue(Instant now) {
    if (!state.held()) {
      return;
    }
    transientFailures = 0;
    requestedGeneration++;
    state = WorkState.READY;
    pendingSince = now;
    nextEligibleAt = now;
    updatedAt = now;
    reasonCode = WorkReason.OPERATOR_REQUEUE;
  }

  void recover(Instant now) {
    if (state != WorkState.RUNNING) {
      throw new IllegalStateException("Only running work can be recovered");
    }
    state = WorkState.READY;
    nextEligibleAt = now;
    updatedAt = now;
    reasonCode = WorkReason.RESTART_RECOVERY;
  }

  WorkView view() {
    return new WorkView(
        paymentIntentId,
        state,
        requestedGeneration,
        completedGeneration,
        transientFailures,
        totalAttempts,
        nextEligibleAt,
        reasonCode,
        invoiceId,
        operationKey,
        lastEventId,
        facts,
        createdAt,
        updatedAt);
  }

  @Override
  public String getId() {
    return paymentIntentId;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  private void markPersisted() {
    newEntity = false;
  }
}
