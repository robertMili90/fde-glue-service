package com.example.fde.glue.reconciliation;

import java.util.List;

public record LinkedInvoicePage(List<Reference> items, String nextCursor) {
  public record Reference(String invoiceId, String paymentIntentId) {}
}
