package com.example.fde.glue.work;

import com.example.fde.glue.config.SyncSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkStore {
  public record Claim(
      String paymentIntentId,
      long generation,
      String attemptId,
      int attemptNumber,
      String eventId) {}

  public record Outcome(
      WorkState state,
      String reason,
      String invoiceId,
      String operationKey,
      String facts,
      long retryAfterMs) {
    public static Outcome failure(SyncFailure failure) {
      return new Outcome(
          failure.state(), failure.reason(), null, null, null, failure.retryAfterMs());
    }
  }

  public record Completion(AttemptOutcome state, String reason) {}

  private static final List<WorkState> CLAIMABLE_STATES =
      List.of(WorkState.READY, WorkState.RETRY_SCHEDULED, WorkState.WAITING_PROVIDER);
  private static final List<WorkState> PENDING_STATES =
      List.of(
          WorkState.READY,
          WorkState.RUNNING,
          WorkState.RETRY_SCHEDULED,
          WorkState.WAITING_PROVIDER);

  private final ReceivedEventRepository receivedEvents;
  private final WorkItemRepository workItems;
  private final AttemptRepository attempts;
  private final Clock clock;
  private final SyncSettings settings;
  private final WorkLock workLock;

  public WorkStore(
      Clock clock,
      SyncSettings settings,
      WorkLock workLock,
      ReceivedEventRepository receivedEvents,
      WorkItemRepository workItems,
      AttemptRepository attempts) {
    this.receivedEvents = receivedEvents;
    this.workItems = workItems;
    this.attempts = attempts;
    this.clock = clock;
    this.settings = settings;
    this.workLock = workLock;
  }

  public boolean accept(
      String stripeEventId, String eventType, String paymentIntentId, String rawBody) {
    workLock.lock();
    if (receivedEvents.existsById(stripeEventId)) {
      return false;
    }
    receivedEvents.save(
        new ReceivedEvent(stripeEventId, eventType, paymentIntentId, rawBody, clock.instant()));
    requestLocked(paymentIntentId, stripeEventId, false);
    // The gate serializes concurrent first receipts; the event PK also enforces
    // uniqueness. Receipt and generation commit together through this proxy.
    return true;
  }

  public void enqueue(String paymentIntentId) {
    workLock.lock();
    requestLocked(paymentIntentId, null, true);
  }

  private void requestLocked(String paymentIntentId, String stripeEventId, boolean coalesce) {
    Instant now = clock.instant();
    WorkItem workItem = workItems.findById(paymentIntentId).orElse(null);
    if (workItem == null) {
      workItem = new WorkItem(paymentIntentId, now);
      workItem.request(stripeEventId, coalesce, now);
      workItems.save(workItem);
    } else {
      workItem.request(stripeEventId, coalesce, now);
    }
  }

  public Claim claim() {
    workLock.lock();
    // Only the single worker calls this with no in-memory claim. A lost claim
    // commit response may leave a RUNNING row without an owner.
    recoverLocked();
    List<WorkItem> eligible =
        workItems.findEligible(CLAIMABLE_STATES, clock.instant(), PageRequest.of(0, 1));
    if (eligible.isEmpty()) {
      return null;
    }
    Instant now = clock.instant();
    Claim claim = eligible.getFirst().claim(UUID.randomUUID().toString(), now);
    attempts.save(new Attempt(claim, now));
    return claim;
  }

  public Completion finish(Claim claim, Outcome outcome) {
    workLock.lock();
    Attempt attempt = attempts.findById(claim.attemptId()).orElse(null);
    WorkItem workItem = workItems.findById(claim.paymentIntentId()).orElse(null);
    if (attempt == null || workItem == null) {
      throw new IllegalStateException("Claimed work or attempt is missing");
    }
    // A completion may have committed even though its caller saw an exception.
    if (attempt.finished()) {
      return attempt.completion();
    }
    Instant now = clock.instant();
    Completion completion = workItem.complete(claim, outcome, settings, now);
    attempt.complete(completion, outcome, now);
    return completion;
  }

  public boolean requeue(String paymentIntentId) {
    workLock.lock();
    WorkItem workItem = workItems.findById(paymentIntentId).orElse(null);
    if (workItem == null) {
      return false;
    }
    workItem.requeue(clock.instant());
    return true;
  }

  public void recover() {
    workLock.lock();
    recoverLocked();
  }

  private void recoverLocked() {
    Instant now = clock.instant();
    for (WorkItem workItem : workItems.findByState(WorkState.RUNNING)) {
      workItem.recover(now);
    }
    for (Attempt attempt : attempts.findByOutcome(AttemptOutcome.RUNNING)) {
      attempt.interrupt(now);
    }
  }

  @Transactional(readOnly = true)
  public WorkView find(String paymentIntentId) {
    WorkItem workItem = workItems.findById(paymentIntentId).orElse(null);
    return workItem == null ? null : workItem.view();
  }

  @Transactional(readOnly = true)
  public List<WorkView> list(String after, int limit) {
    return workItems
        .findByPaymentIntentIdGreaterThanOrderByPaymentIntentId(after, PageRequest.of(0, limit))
        .stream()
        .map(WorkItem::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AttemptView> attempts(String paymentIntentId, int limit) {
    return attempts
        .findByPaymentIntentIdOrderByStartedAtDescAttemptNumberDesc(
            paymentIntentId, PageRequest.of(0, limit))
        .stream()
        .map(Attempt::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public long countState(WorkState state) {
    return workItems.countByState(state);
  }

  @Transactional(readOnly = true)
  public double oldestAgeSeconds() {
    Instant oldest = workItems.findOldestPendingSince(PENDING_STATES).orElse(null);
    return oldest == null
        ? 0
        : Math.max(0, Duration.between(oldest, clock.instant()).toMillis() / 1000.0);
  }
}
