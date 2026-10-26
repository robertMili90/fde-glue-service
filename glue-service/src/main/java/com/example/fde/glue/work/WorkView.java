package com.example.fde.glue.work;

import java.time.Instant;

public record WorkView(
    String paymentIntentId,
    WorkState state,
    long requestedGeneration,
    long completedGeneration,
    int transientFailures,
    int totalAttempts,
    Instant nextEligibleAt,
    String reasonCode,
    String invoiceId,
    String operationKey,
    String lastEventId,
    String facts,
    Instant createdAt,
    Instant updatedAt) {}
