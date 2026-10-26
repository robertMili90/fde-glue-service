package com.example.fde.erp.invoice;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceView(
    String id,
    long expectedAmountMinor,
    String currency,
    String paymentIntentId,
    long version,
    FinancialStatus financialStatus,
    PaymentProjection paymentProjection)
    implements InvoiceResponse {}
