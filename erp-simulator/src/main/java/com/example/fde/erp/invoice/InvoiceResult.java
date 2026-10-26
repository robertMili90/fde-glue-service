package com.example.fde.erp.invoice;

public record InvoiceResult(int status, InvoiceResponse body, int delayMs) {
  static InvoiceResult problem(int status, String reasonCode) {
    return new InvoiceResult(status, new InvoiceProblem(reasonCode, reasonCode), 0);
  }
}
