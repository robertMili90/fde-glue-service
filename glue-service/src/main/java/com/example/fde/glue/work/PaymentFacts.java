package com.example.fde.glue.work;

public record PaymentFacts(
    String paymentIntentId,
    String status,
    String currency,
    long requestedAmountMinor,
    long collectedMinor,
    long refundedMinor,
    boolean pendingRefund,
    String captureMethod) {}
