package com.example.fde.glue.work;

import java.time.Instant;

public record AttemptView(
    String id,
    long generation,
    int attemptNumber,
    Instant startedAt,
    Instant finishedAt,
    AttemptOutcome outcome,
    String reasonCode,
    String invoiceId,
    String operationKey,
    String facts) {}
