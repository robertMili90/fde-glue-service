package com.example.fde.glue.reconciliation;

import java.time.Instant;

public record ReconciliationRunView(
    String id,
    ReconciliationState state,
    String cursor,
    long enqueuedReferences,
    long pagesProcessed,
    int failedBatches,
    String lastFailure,
    Instant startedAt,
    Instant updatedAt,
    Instant completedAt) {}
