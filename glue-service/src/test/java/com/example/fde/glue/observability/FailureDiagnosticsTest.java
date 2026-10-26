package com.example.fde.glue.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.fde.glue.config.SyncSettings;
import com.example.fde.glue.erp.ErpClient;
import com.example.fde.glue.reconciliation.Reconciler;
import com.example.fde.glue.reconciliation.ReconciliationRunView;
import com.example.fde.glue.reconciliation.ReconciliationState;
import com.example.fde.glue.reconciliation.ReconciliationStore;
import com.example.fde.glue.work.AttemptOutcome;
import com.example.fde.glue.work.SyncEngine;
import com.example.fde.glue.work.SyncWorker;
import com.example.fde.glue.work.WorkState;
import com.example.fde.glue.work.WorkStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

class FailureDiagnosticsTest {
  private static final String PRIVATE = "sk_test_private raw-payload private-invoice-value";
  private static final SyncSettings SETTINGS =
      new SyncSettings(true, 5, 1000, 60000, 30000, 500, 100, 10, 10000);

  @Test
  void causeTypesStackLocationsAndDatabaseCodesSurviveWithoutMessagesOrValues() {
    var database = new SQLException(PRIVATE, "23505", 23505);
    var failure = new IllegalStateException(PRIVATE, database);
    String diagnostic = FailureDiagnostics.describe(failure);
    assertThat(diagnostic)
        .contains(
            "java.lang.IllegalStateException",
            "java.sql.SQLException",
            "sqlState=23505",
            "vendorCode=23505",
            "FailureDiagnosticsTest.java:")
        .doesNotContain(PRIVATE, "sk_test_private", "raw-payload");
    // Malformed cause chains cannot make failure reporting hang.
    var first = new IllegalStateException(PRIVATE);
    var second = new IllegalArgumentException(PRIVATE);
    first.initCause(second);
    second.initCause(first);
    assertThat(FailureDiagnostics.describe(first)).hasSizeLessThan(12000).doesNotContain(PRIVATE);
  }

  @Test
  void unexpectedWorkerFailureLogsCauseLocationsAndKeepsStablePublicReason() {
    WorkStore store = mock(WorkStore.class);
    SyncEngine engine = mock(SyncEngine.class);
    WorkStore.Claim claim =
        new WorkStore.Claim("pi_diagnostic", 1, "attempt_diagnostic", 1, "evt_diagnostic");
    when(store.claim()).thenReturn(claim);
    when(engine.sync(claim.paymentIntentId()))
        .thenThrow(new IllegalStateException(PRIVATE, new NullPointerException(PRIVATE)));
    when(store.finish(any(), any()))
        .thenReturn(new WorkStore.Completion(AttemptOutcome.FAILED, "INTERNAL_ERROR"));
    Logger logger = (Logger) LoggerFactory.getLogger(SyncWorker.class);
    ListAppender<ILoggingEvent> captured = capture(logger);
    var meters = new SimpleMeterRegistry();
    try {
      new SyncWorker(store, engine, SETTINGS, meters).tick();
      ArgumentCaptor<WorkStore.Outcome> outcome = ArgumentCaptor.forClass(WorkStore.Outcome.class);
      verify(store).finish(any(), outcome.capture());
      assertThat(outcome.getValue().state()).isEqualTo(WorkState.FAILED);
      assertThat(outcome.getValue().reason()).isEqualTo("INTERNAL_ERROR");
      assertThat(messages(captured))
          .contains(
              "pi_diagnostic",
              "attempt_diagnostic",
              "java.lang.NullPointerException",
              "FailureDiagnosticsTest.java:")
          .doesNotContain(PRIVATE);
    } finally {
      meters.close();
      logger.detachAppender(captured);
    }
  }

  @Test
  void completionPersistenceFailureLogsSanitizedCauseAndRetainsObservationForRetry() {
    WorkStore store = mock(WorkStore.class);
    SyncEngine engine = mock(SyncEngine.class);
    var claim = new WorkStore.Claim("pi_retained", 1, "attempt_retained", 1, "evt_retained");
    var outcome =
        new WorkStore.Outcome(WorkState.DONE, "SYNCHRONIZED", "invoice", "operation", null, 0);
    when(store.claim()).thenReturn(claim);
    when(engine.sync(claim.paymentIntentId())).thenReturn(outcome);
    when(store.finish(claim, outcome))
        .thenThrow(
            new DataAccessResourceFailureException(PRIVATE, new SQLException(PRIVATE, "08006", 7)))
        .thenReturn(new WorkStore.Completion(AttemptOutcome.DONE, "SYNCHRONIZED"));
    Logger logger = (Logger) LoggerFactory.getLogger(SyncWorker.class);
    ListAppender<ILoggingEvent> captured = capture(logger);
    var meters = new SimpleMeterRegistry();
    try {
      SyncWorker worker = new SyncWorker(store, engine, SETTINGS, meters);
      worker.tick();
      worker.tick();
      verify(engine, times(1)).sync(claim.paymentIntentId());
      verify(store, times(2)).finish(claim, outcome);
      assertThat(messages(captured))
          .contains("pi_retained", "sqlState=08006", "vendorCode=7", "FailureDiagnosticsTest.java:")
          .doesNotContain(PRIVATE);
    } finally {
      meters.close();
      logger.detachAppender(captured);
    }
  }

  @Test
  void reconciliationPreservesOriginalDiagnosticsWhenFailurePersistenceAlsoFails() {
    ReconciliationStore store = mock(ReconciliationStore.class);
    ErpClient erp = mock(ErpClient.class);
    Instant now = Instant.parse("2026-10-24T00:00:00Z");
    when(store.claim())
        .thenReturn(
            new ReconciliationRunView(
                "run_diagnostic",
                ReconciliationState.RUNNING,
                null,
                0,
                0,
                0,
                null,
                now,
                now,
                null));
    when(erp.list(null, 100))
        .thenThrow(new IllegalStateException(PRIVATE, new NullPointerException(PRIVATE)));
    doThrow(new DataAccessResourceFailureException(PRIVATE, new SQLException(PRIVATE, "08006", 7)))
        .when(store)
        .fail("run_diagnostic", "RECONCILIATION_ERROR");
    Logger logger = (Logger) LoggerFactory.getLogger(Reconciler.class);
    ListAppender<ILoggingEvent> captured = capture(logger);
    try {
      new Reconciler(store, erp, SETTINGS).tick();
      assertThat(messages(captured))
          .contains(
              "run_diagnostic",
              "RECONCILIATION_ERROR",
              "java.lang.NullPointerException",
              "sqlState=08006",
              "FailureDiagnosticsTest.java:")
          .doesNotContain(PRIVATE);
      verify(store).fail("run_diagnostic", "RECONCILIATION_ERROR");
    } finally {
      logger.detachAppender(captured);
    }
  }

  private ListAppender<ILoggingEvent> capture(Logger logger) {
    var captured = new ListAppender<ILoggingEvent>();
    captured.start();
    logger.addAppender(captured);
    return captured;
  }

  private String messages(ListAppender<ILoggingEvent> captured) {
    return String.join(
        "\n", captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
  }
}
