package com.example.fde.glue.work;

import com.example.fde.glue.config.SyncSettings;
import com.example.fde.glue.observability.FailureDiagnostics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncWorker {
  private static final Logger log = LoggerFactory.getLogger(SyncWorker.class);
  private final WorkStore store;
  private final SyncEngine engine;
  private final SyncSettings settings;
  private final MeterRegistry metrics;
  private WorkStore.Claim pendingClaim;
  private WorkStore.Outcome pendingOutcome;

  public SyncWorker(
      WorkStore store, SyncEngine engine, SyncSettings settings, MeterRegistry metrics) {
    this.store = store;
    this.engine = engine;
    this.settings = settings;
    this.metrics = metrics;
    for (WorkState state : WorkState.values()) {
      if (state != WorkState.DONE) {
        Gauge.builder("sync.work", store, value -> value.countState(state))
            .tag("state", state.name())
            .register(metrics);
      }
    }
    Gauge.builder("sync.oldest.work.seconds", store, WorkStore::oldestAgeSeconds).register(metrics);
  }

  @Scheduled(
      fixedDelayString = "${sync.poll-ms:500}",
      initialDelayString = "${sync.initial-delay-ms:1000}")
  void scheduled() {
    if (settings.enabled()) {
      tick();
    }
  }

  public synchronized void tick() {
    try {
      if (pendingClaim == null) {
        pendingClaim = store.claim();
        if (pendingClaim == null) {
          return;
        }
        pendingOutcome = observePayment(pendingClaim);
      }
      WorkStore.Completion completion = store.finish(pendingClaim, pendingOutcome);
      log.info(
          "sync payment={} event={} invoice={} operation={} attempt={} outcome={} reason={}",
          pendingClaim.paymentIntentId(),
          pendingClaim.eventId(),
          pendingOutcome.invoiceId(),
          pendingOutcome.operationKey(),
          pendingClaim.attemptId(),
          completion.state(),
          completion.reason());
      pendingClaim = null;
      pendingOutcome = null;
    } catch (RuntimeException persistence) {
      // Keep the claim and observation until completion commits; never make
      // a second remote effect just because the local commit response failed.
      String paymentIntentId = pendingClaim == null ? null : pendingClaim.paymentIntentId();
      log.error(
          "sync persistence unavailable; completion retained; payment={} diagnostic={}",
          paymentIntentId,
          FailureDiagnostics.describe(persistence));
    }
  }

  private WorkStore.Outcome observePayment(WorkStore.Claim claim) {
    Timer.Sample sample = Timer.start(metrics);
    try {
      return engine.sync(claim.paymentIntentId());
    } catch (SyncFailure failure) {
      log.warn(
          "sync provider read failed; payment={} attempt={} reason={} diagnostic={}",
          claim.paymentIntentId(),
          claim.attemptId(),
          failure.reason(),
          FailureDiagnostics.describe(failure));
      return WorkStore.Outcome.failure(failure);
    } catch (RuntimeException unexpected) {
      log.error(
          "sync observation failed; payment={} attempt={} diagnostic={}",
          claim.paymentIntentId(),
          claim.attemptId(),
          FailureDiagnostics.describe(unexpected));
      return new WorkStore.Outcome(
          WorkState.FAILED, WorkReason.INTERNAL_ERROR, null, null, null, 0);
    } finally {
      sample.stop(metrics.timer("sync.attempt.latency"));
    }
  }
}
