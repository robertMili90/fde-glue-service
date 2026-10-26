package com.example.fde.glue.erp;

// Provider responses keep their raw status text so unknown values reach the
// conflict policy rather than changing a financial conflict into a decoding error.
public record InvoiceSnapshot(
    String id,
    long expectedAmountMinor,
    String currency,
    String paymentIntentId,
    long version,
    String financialStatus,
    PaymentProjection paymentProjection) {}
