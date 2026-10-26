package com.example.fde.glue.work;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:work_repository;DB_CLOSE_DELAY=-1",
      "sync.enabled=false",
      "stripe.secret-key=",
      "stripe.webhook-secret=",
      "logging.level.root=WARN"
    })
@Import(WorkPersistenceTest.TestTime.class)
class WorkPersistenceTest {
  private static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");
  private final WorkStore work;
  private final ReceivedEventRepository receivedEvents;
  private final JdbcTemplate jdbc;

  @Autowired
  WorkPersistenceTest(WorkStore work, ReceivedEventRepository receivedEvents, JdbcTemplate jdbc) {
    this.work = work;
    this.receivedEvents = receivedEvents;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void clearWork() {
    jdbc.update("delete from glue.attempt");
    jdbc.update("delete from glue.work_item");
    jdbc.update("delete from glue.received_event");
  }

  @Test
  void assignedEventIdentityCannotOverwriteAnAcceptedReceipt() {
    assertThat(work.accept("evt_original", "payment_intent.succeeded", "pi_original", "original"))
        .isTrue();
    assertThatThrownBy(
            () ->
                receivedEvents.save(
                    new ReceivedEvent(
                        "evt_original", "refund.updated", "pi_other", "changed", NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "select payload from glue.received_event where id = 'evt_original'", String.class))
        .isEqualTo("original");
    assertThat(work.find("pi_original").requestedGeneration()).isEqualTo(1);
    assertThat(work.find("pi_other")).isNull();
  }

  @Test
  void claimsDueWorkInStableOrderAndLeavesFutureWorkScheduled() {
    work.enqueue("pi_d");
    work.enqueue("pi_c");
    work.enqueue("pi_b");
    work.enqueue("pi_a");
    jdbc.update(
        "update glue.work_item set next_eligible_at = ? where payment_intent_id = ?",
        NOW.minusSeconds(10).atOffset(ZoneOffset.UTC),
        "pi_c");
    jdbc.update(
        "update glue.work_item set next_eligible_at = ? where payment_intent_id = ?",
        NOW.plusSeconds(10).atOffset(ZoneOffset.UTC),
        "pi_b");

    finishNext("pi_c", WorkState.DONE);
    finishNext("pi_a", WorkState.DONE);
    finishNext("pi_d", WorkState.DONE);
    assertThat(work.claim()).isNull();
    assertThat(work.find("pi_b").state()).isEqualTo(WorkState.READY);
    assertThat(work.find("pi_b").totalAttempts()).isZero();
  }

  @Test
  void workPagesAndAttemptHistoryRetainOrderingAndLimits() {
    work.enqueue("pi_c");
    work.enqueue("pi_b");
    work.enqueue("pi_a");
    assertThat(work.list("", 2))
        .extracting(WorkView::paymentIntentId)
        .containsExactly("pi_a", "pi_b");
    assertThat(work.list("pi_a", 1)).extracting(WorkView::paymentIntentId).containsExactly("pi_b");
    assertThat(work.list("pi_c", 2)).isEmpty();

    finishNext("pi_a", WorkState.DONE);
    assertThat(work.accept("evt_again", "payment_intent.succeeded", "pi_a", "another event"))
        .isTrue();
    finishNext("pi_a", WorkState.DONE);
    assertThat(work.attempts("pi_a", 10))
        .extracting(AttemptView::attemptNumber)
        .containsExactly(2, 1);
    assertThat(work.attempts("pi_a", 1)).extracting(AttemptView::attemptNumber).containsExactly(2);
    assertThat(work.attempts("pi_b", 10)).isEmpty();
  }

  @Test
  void cancellationCompletionPreservesRequestsReceivedDuringWork() {
    work.enqueue("pi_canceled");
    WorkStore.Claim first = work.claim();
    assertThat(
            work.accept(
                "evt_during_cancel", "payment_intent.payment_failed", "pi_canceled", "event"))
        .isTrue();
    var canceled =
        new WorkStore.Outcome(WorkState.DONE, WorkReason.PAYMENT_CANCELED, null, null, null, 0);
    work.finish(first, canceled);
    assertThat(work.find("pi_canceled").state()).isEqualTo(WorkState.READY);
    assertThat(work.find("pi_canceled").completedGeneration()).isEqualTo(1);
    WorkStore.Claim second = work.claim();
    assertThat(second.generation()).isEqualTo(2);
    work.finish(second, canceled);
    assertThat(work.find("pi_canceled").state()).isEqualTo(WorkState.DONE);
    assertThat(work.find("pi_canceled").completedGeneration()).isEqualTo(2);
    assertThat(work.find("pi_canceled").nextEligibleAt()).isNull();
    work.enqueue("pi_canceled");
    assertThat(work.claim()).isNull();
    assertThat(work.find("pi_canceled").requestedGeneration()).isEqualTo(2);
  }

  @Test
  void pendingMetricsExcludeCompletedAndHeldWork() {
    assertThat(work.oldestAgeSeconds()).isZero();
    work.enqueue("pi_held");
    jdbc.update(
        "update glue.work_item set pending_since = ? where payment_intent_id = ?",
        NOW.minusSeconds(90).atOffset(ZoneOffset.UTC),
        "pi_held");
    assertThat(work.oldestAgeSeconds()).isEqualTo(90);
    finishNext("pi_held", WorkState.NEEDS_REVIEW);
    assertThat(work.oldestAgeSeconds()).isZero();

    work.enqueue("pi_pending");
    jdbc.update(
        "update glue.work_item set pending_since = ? where payment_intent_id = ?",
        NOW.minusSeconds(15).atOffset(ZoneOffset.UTC),
        "pi_pending");
    assertThat(work.oldestAgeSeconds()).isEqualTo(15);
    assertThat(work.countState(WorkState.READY)).isEqualTo(1);
    assertThat(work.countState(WorkState.NEEDS_REVIEW)).isEqualTo(1);
    finishNext("pi_pending", WorkState.DONE);
    assertThat(work.oldestAgeSeconds()).isZero();
    assertThat(work.countState(WorkState.DONE)).isEqualTo(1);
    assertThat(work.countState(WorkState.READY)).isZero();
  }

  private void finishNext(String paymentIntentId, WorkState state) {
    WorkStore.Claim claim = work.claim();
    assertThat(claim).isNotNull();
    assertThat(claim.paymentIntentId()).isEqualTo(paymentIntentId);
    work.finish(claim, new WorkStore.Outcome(state, "TEST_OBSERVATION", null, null, null, 0));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestTime {
    @Bean
    @Primary
    Clock persistenceTestClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
