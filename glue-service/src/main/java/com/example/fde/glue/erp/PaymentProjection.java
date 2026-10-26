package com.example.fde.glue.erp;

public record PaymentProjection(
    String paymentIntentId,
    String currency,
    long amountCollectedMinor,
    long amountRefundedMinor,
    String status) {}
