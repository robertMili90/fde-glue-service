package com.example.fde.erp.demo;

import com.example.fde.erp.invoice.CreateInvoice;
import com.example.fde.erp.invoice.ErpStore;
import com.example.fde.erp.invoice.FinancialStatus;
import com.example.fde.erp.invoice.InvoiceResult;
import com.example.fde.erp.invoice.InvoiceView;
import com.example.fde.erp.invoice.PaymentProjection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoInvoiceSeeder implements ApplicationRunner {
  private record Fixture(
      String invoiceId, String paymentIntentId, long amountMinor, FinancialStatus status) {}

  private static final Logger log = LoggerFactory.getLogger(DemoInvoiceSeeder.class);
  private static final List<Fixture> FIXTURES =
      List.of(
          new Fixture("inv_demo", "pi_demo", 1200, FinancialStatus.PAID),
          new Fixture("inv_refunded", "pi_refunded", 1200, FinancialStatus.REFUNDED),
          new Fixture("inv_review", "pi_review", 1300, FinancialStatus.UNPAID),
          new Fixture("inv_unpaid", "pi_unpaid", 1200, FinancialStatus.UNPAID));

  private final ErpStore invoices;

  DemoInvoiceSeeder(ErpStore invoices) {
    this.invoices = invoices;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    // All fixture writes/idempotency outcomes commit together; no HTTP calls occur here.
    for (Fixture fixture : FIXTURES) {
      seed(fixture);
    }
    log.info("Local demo invoices ready; existing invoices and financial edits preserved");
  }

  private void seed(Fixture fixture) {
    InvoiceResult existing = invoices.get(fixture.invoiceId());
    if (existing.status() == 200) {
      InvoiceView invoice = (InvoiceView) existing.body();
      if (!invoice.paymentIntentId().equals(fixture.paymentIntentId())
          || invoice.expectedAmountMinor() != fixture.amountMinor()
          || !invoice.currency().equals("USD")) {
        throw new IllegalStateException("Demo invoice identity conflict: " + fixture.invoiceId());
      }
      return;
    }
    InvoiceResult created =
        invoices.create(
            new CreateInvoice(
                fixture.invoiceId(), fixture.amountMinor(), "USD", fixture.paymentIntentId()));
    requireSuccess(created, 201, fixture.invoiceId());
    if (fixture.status() == FinancialStatus.UNPAID) {
      return;
    }
    long refundedMinor = fixture.status() == FinancialStatus.REFUNDED ? fixture.amountMinor() : 0;
    String operationKey =
        fixture.invoiceId().equals("inv_demo") ? "demo-paid-v1" : "demo-refunded-fixture-v1";
    InvoiceResult projected =
        invoices.seedDemoProjection(
            fixture.invoiceId(),
            operationKey,
            1,
            new PaymentProjection(
                fixture.paymentIntentId(),
                "USD",
                fixture.amountMinor(),
                refundedMinor,
                fixture.status().name()));
    requireSuccess(projected, 200, fixture.invoiceId());
  }

  private void requireSuccess(InvoiceResult result, int expectedStatus, String invoiceId) {
    if (result.status() != expectedStatus) {
      throw new IllegalStateException(
          "Cannot seed demo invoice " + invoiceId + ": " + result.status());
    }
  }
}
