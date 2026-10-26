package com.example.fde.glue.work;

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
@Table(name = "attempt", schema = "glue")
class Attempt implements Persistable<String> {
  // Assigned IDs must still use insert semantics for new instances.
  @Transient private boolean newEntity = true;

  @Id
  @Column(length = 36)
  private String id;

  @Column(nullable = false, length = 255)
  private String paymentIntentId;

  private long generation;
  private int attemptNumber;

  @Column(nullable = false)
  private Instant startedAt;

  private Instant finishedAt;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 32)
  private AttemptOutcome outcome;

  @Column(length = 100)
  private String reasonCode;

  @Column(length = 255)
  private String invoiceId;

  @Column(length = 255)
  private String operationKey;

  @Lob private String facts;

  protected Attempt() {}

  Attempt(WorkStore.Claim claim, Instant now) {
    id = claim.attemptId();
    paymentIntentId = claim.paymentIntentId();
    generation = claim.generation();
    attemptNumber = claim.attemptNumber();
    startedAt = now;
    outcome = AttemptOutcome.RUNNING;
  }

  boolean finished() {
    return finishedAt != null;
  }

  WorkStore.Completion completion() {
    return new WorkStore.Completion(outcome, reasonCode);
  }

  void complete(WorkStore.Completion completion, WorkStore.Outcome observation, Instant now) {
    finishedAt = now;
    outcome = completion.state();
    reasonCode = completion.reason();
    invoiceId = observation.invoiceId();
    operationKey = observation.operationKey();
    facts = observation.facts();
  }

  void interrupt(Instant now) {
    outcome = AttemptOutcome.INTERRUPTED;
    reasonCode = WorkReason.RESTART_RECOVERY;
    finishedAt = now;
  }

  AttemptView view() {
    return new AttemptView(
        id,
        generation,
        attemptNumber,
        startedAt,
        finishedAt,
        outcome,
        reasonCode,
        invoiceId,
        operationKey,
        facts);
  }

  @Override
  public String getId() {
    return id;
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
