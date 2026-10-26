package com.example.fde.erp.invoice.api;

import com.example.fde.erp.invoice.CreateInvoice;
import com.example.fde.erp.invoice.ErpStore;
import com.example.fde.erp.invoice.InvoicePage;
import com.example.fde.erp.invoice.InvoiceResponse;
import com.example.fde.erp.invoice.InvoiceResult;
import com.example.fde.erp.invoice.InvoiceView;
import com.example.fde.erp.invoice.PaymentProjection;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class InvoiceController {
  private final ErpStore store;

  InvoiceController(ErpStore store) {
    this.store = store;
  }

  @PostMapping("/invoices")
  ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoice request) {
    return response(store.create(request));
  }

  @GetMapping("/invoices/{invoiceId}")
  ResponseEntity<InvoiceResponse> get(@PathVariable String invoiceId) {
    return response(store.get(invoiceId));
  }

  @GetMapping("/invoices/by-payment/{paymentIntentId}")
  ResponseEntity<InvoiceResponse> byPayment(@PathVariable String paymentIntentId) {
    return response(store.byPayment(paymentIntentId));
  }

  @GetMapping("/invoices")
  InvoicePage list(
      @RequestParam(defaultValue = "") String cursor,
      @RequestParam(defaultValue = "100") int limit) {
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit must be 1..100");
    }
    return store.list(cursor, limit);
  }

  @PutMapping("/invoices/{invoiceId}/payment-projection")
  ResponseEntity<InvoiceResponse> project(
      @PathVariable String invoiceId,
      @RequestHeader("Idempotency-Key") String operationKey,
      @RequestHeader("If-Match") String expectedVersion,
      @Valid @RequestBody PaymentProjection projection)
      throws InterruptedException {
    long version = parseVersion(operationKey, expectedVersion);
    InvoiceResult result = store.project(invoiceId, operationKey, version, projection);
    // The managed store transaction commits before the simulated HTTP delay.
    if (result.delayMs() > 0) {
      Thread.sleep(result.delayMs());
    }
    return response(result);
  }

  private long parseVersion(String operationKey, String expectedVersion) {
    if (operationKey.isBlank()
        || operationKey.length() > 255
        || !expectedVersion.matches("\"[0-9]+\"")) {
      throw new IllegalArgumentException(
          "Expected a nonempty idempotency key and quoted integer If-Match");
    }
    try {
      return Long.parseLong(expectedVersion.substring(1, expectedVersion.length() - 1));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException("Invalid invoice version", invalid);
    }
  }

  private ResponseEntity<InvoiceResponse> response(InvoiceResult result) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.status());
    if (result.body() instanceof InvoiceView invoice) {
      builder.eTag(Long.toString(invoice.version()));
    }
    return builder.body(result.body());
  }
}
