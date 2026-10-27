package com.example.fde.erp.invoice;

import com.example.fde.erp.demo.DemoControls;
import com.example.fde.erp.demo.FaultEffect;
import com.example.fde.erp.demo.FaultMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ErpStore {
  private enum ProjectionMode {
    REQUEST,
    DEMO_SEED
  }

  private enum Reason {
    BUSINESS_IDENTITY_CONFLICT,
    INVOICE_NOT_FOUND,
    IDEMPOTENCY_PAYLOAD_CONFLICT,
    TEMPORARY_FAILURE,
    STALE_INVOICE_VERSION,
    FINANCIAL_CONFLICT
  }

  private final InvoiceRepository invoices;
  private final IdempotencyResultRepository idempotencyResults;
  private final ObjectMapper json;
  private final DemoControls controls;

  public ErpStore(
      ObjectMapper json,
      DemoControls controls,
      InvoiceRepository invoices,
      IdempotencyResultRepository idempotencyResults) {
    this.invoices = invoices;
    this.idempotencyResults = idempotencyResults;
    this.json = json;
    this.controls = controls;
  }

  public InvoiceResult create(CreateInvoice request) {
    controls.lockMutations();
    if (invoices.existsById(request.id())
        || invoices.existsByPaymentIntentId(request.paymentIntentId())) {
      return problem(409, Reason.BUSINESS_IDENTITY_CONFLICT);
    }
    Invoice invoice = new Invoice(request);
    invoices.save(invoice);
    return new InvoiceResult(201, invoice.view(), 0);
  }

  @Transactional(readOnly = true)
  public InvoiceResult get(String invoiceId) {
    return found(invoices.findById(invoiceId).orElse(null));
  }

  @Transactional(readOnly = true)
  public InvoiceResult byPayment(String paymentIntentId) {
    return found(invoices.findByPaymentIntentId(paymentIntentId).orElse(null));
  }

  private InvoiceResult found(Invoice invoice) {
    return invoice == null
        ? problem(404, Reason.INVOICE_NOT_FOUND)
        : new InvoiceResult(200, invoice.view(), 0);
  }

  @Transactional(readOnly = true)
  public InvoicePage list(String cursor, int limit) {
    List<Invoice> page =
        invoices.findByIdGreaterThanOrderById(cursor, PageRequest.of(0, limit + 1));
    String nextCursor = page.size() > limit ? page.get(limit - 1).id() : null;
    return new InvoicePage(page.stream().limit(limit).map(Invoice::view).toList(), nextCursor);
  }

  public InvoiceResult project(
      String invoiceId, String operationKey, long expectedVersion, PaymentProjection projection) {
    return project(invoiceId, operationKey, expectedVersion, projection, ProjectionMode.REQUEST);
  }

  public InvoiceResult seedDemoProjection(
      String invoiceId, String operationKey, long expectedVersion, PaymentProjection projection) {
    // Startup fixture initialization must not consume persisted faults reserved for API requests.
    return project(invoiceId, operationKey, expectedVersion, projection, ProjectionMode.DEMO_SEED);
  }

  private InvoiceResult project(
      String invoiceId,
      String operationKey,
      long expectedVersion,
      PaymentProjection projection,
      ProjectionMode mode) {
    controls.lockMutations();
    String payload = encode(List.of(invoiceId, projection));
    IdempotencyResult existing = idempotencyResults.findById(operationKey).orElse(null);
    if (existing != null) {
      if (!existing.matches(payload)) {
        return problem(409, Reason.IDEMPOTENCY_PAYLOAD_CONFLICT);
      }
      return new InvoiceResult(200, decode(existing.result()), 0);
    }
    Invoice invoice = invoices.findById(invoiceId).orElse(null);
    if (invoice == null) {
      return problem(404, Reason.INVOICE_NOT_FOUND);
    }
    // Replay resolves first: neither a replay nor changed-payload conflict
    // consumes synthetic faults or evaluates a stale expected version.
    FaultEffect fault =
        mode == ProjectionMode.REQUEST
            ? controls.consumeFault()
            : new FaultEffect(FaultMode.NONE, 0);
    if (fault.mode() == FaultMode.FAIL_BEFORE_WRITE) {
      return problem(503, Reason.TEMPORARY_FAILURE);
    }
    if (fault.mode() == FaultMode.VERSION_CONFLICT) {
      invoice.simulateConcurrentChange();
      return problem(412, Reason.STALE_INVOICE_VERSION);
    }
    if (!invoice.hasVersion(expectedVersion)) {
      return problem(412, Reason.STALE_INVOICE_VERSION);
    }
    if (!invoice.compatible(projection)) {
      return problem(422, Reason.FINANCIAL_CONFLICT);
    }
    if (invoice.project(projection)) {
      controls.appliedUpdate();
    }
    idempotencyResults.save(new IdempotencyResult(operationKey, payload, encode(invoice.view())));
    int delayMs = fault.mode() == FaultMode.APPLY_THEN_DELAY ? fault.delayMs() : 0;
    return new InvoiceResult(200, invoice.view(), delayMs);
  }

  private InvoiceResult problem(int status, Reason reason) {
    return InvoiceResult.problem(status, reason.name());
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Cannot encode ERP result", failure);
    }
  }

  private InvoiceView decode(String value) {
    try {
      return json.readValue(value, InvoiceView.class);
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Cannot decode ERP result", failure);
    }
  }
}
