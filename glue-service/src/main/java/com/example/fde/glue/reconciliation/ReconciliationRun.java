package com.example.fde.glue.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "reconciliation_run", schema = "glue")
class ReconciliationRun implements Persistable<String> {
  // Assigned IDs must still use insert semantics for new instances.
  @Transient private boolean newEntity = true;

  @Id
  @Column(length = 36)
  private String id;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 32)
  private ReconciliationState state;

  @Column(length = 255)
  private String cursor;

  private long enqueuedReferences;
  private long pagesProcessed;
  private int failedBatches;

  @Column(length = 100)
  private String lastFailure;

  @Column(nullable = false)
  private Instant startedAt;

  @Column(nullable = false)
  private Instant updatedAt;

  private Instant completedAt;

  protected ReconciliationRun() {}

  ReconciliationRun(String id, Instant now) {
    this.id = id;
    state = ReconciliationState.QUEUED;
    startedAt = now;
    updatedAt = now;
  }

  boolean complete() {
    return state == ReconciliationState.COMPLETE;
  }

  ReconciliationState state() {
    return state;
  }

  String id() {
    return id;
  }

  String cursor() {
    return cursor;
  }

  long pagesProcessed() {
    return pagesProcessed;
  }

  void request(Instant now) {
    if (state == ReconciliationState.FAILED) {
      state = ReconciliationState.QUEUED;
      updatedAt = now;
    }
  }

  boolean claim(Instant now) {
    if (state != ReconciliationState.QUEUED
        && state != ReconciliationState.INCOMPLETE
        && state != ReconciliationState.RUNNING) {
      return false;
    }
    // A single synchronized reconciler can reclaim its own lost claim result.
    state = ReconciliationState.RUNNING;
    updatedAt = now;
    return true;
  }

  void requireRunning() {
    if (state != ReconciliationState.RUNNING) {
      throw new IllegalStateException("Scan ownership changed");
    }
  }

  void checkpoint(int referenceCount, String nextCursor, Instant now) {
    requireRunning();
    enqueuedReferences += referenceCount;
    pagesProcessed++;
    cursor = nextCursor;
    updatedAt = now;
    if (nextCursor == null) {
      state = ReconciliationState.COMPLETE;
      completedAt = now;
    }
  }

  void pause(Instant now) {
    requireRunning();
    state = ReconciliationState.INCOMPLETE;
    updatedAt = now;
  }

  void fail(String reasonCode, Instant now) {
    requireRunning();
    state = ReconciliationState.FAILED;
    lastFailure = reasonCode;
    failedBatches++;
    updatedAt = now;
  }

  ReconciliationRunView view() {
    return new ReconciliationRunView(
        id,
        state,
        cursor,
        enqueuedReferences,
        pagesProcessed,
        failedBatches,
        lastFailure,
        startedAt,
        updatedAt,
        completedAt);
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
