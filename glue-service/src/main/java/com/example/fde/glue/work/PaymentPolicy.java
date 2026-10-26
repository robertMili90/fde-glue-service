package com.example.fde.glue.work;

import com.example.fde.glue.erp.InvoiceSnapshot;
import com.example.fde.glue.erp.PaymentProjection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

public final class PaymentPolicy {
  enum PaymentStage {
    WAITING,
    CANCELED,
    SUCCEEDED
  }

  private enum FinancialStatus {
    UNPAID,
    PAID,
    REFUNDED
  }

  private enum Conflict {
    INVOICE_ASSOCIATION_MISMATCH,
    INVOICE_FINANCIAL_CONFLICT,
    AMOUNT_MISMATCH,
    CURRENCY_MISMATCH,
    UNSUPPORTED_CAPTURE,
    UNKNOWN_PAYMENT_STATUS,
    INVALID_REFUND_TOTAL,
    PARTIAL_REFUND
  }

  static PaymentStage paymentStage(String status) {
    if (status == null) {
      throw review(Conflict.UNKNOWN_PAYMENT_STATUS);
    }
    return switch (status) {
      case "requires_payment_method", "requires_confirmation", "requires_action", "processing" ->
          PaymentStage.WAITING;
      case "canceled" -> PaymentStage.CANCELED;
      case "succeeded" -> PaymentStage.SUCCEEDED;
      case "requires_capture" -> throw review(Conflict.UNSUPPORTED_CAPTURE);
      default -> throw review(Conflict.UNKNOWN_PAYMENT_STATUS);
    };
  }

  public static void requireAssociation(String paymentIntentId, InvoiceSnapshot invoice) {
    if (invoice.id() == null
        || invoice.version() < 1
        || !paymentIntentId.equals(invoice.paymentIntentId())) {
      throw review(Conflict.INVOICE_ASSOCIATION_MISMATCH);
    }
  }

  public static PaymentProjection normalize(PaymentFacts facts, InvoiceSnapshot invoice) {
    FinancialStatus invoiceStatus = financialStatus(invoice.financialStatus());
    requireCompatibleInvoice(invoice, invoiceStatus);
    requireCompatibleCollection(facts, invoice);
    FinancialStatus projectionStatus = refundedStatus(facts);
    if (invoiceStatus == FinancialStatus.REFUNDED && projectionStatus != FinancialStatus.REFUNDED) {
      throw review(Conflict.INVOICE_FINANCIAL_CONFLICT);
    }
    return new PaymentProjection(
        facts.paymentIntentId(),
        facts.currency(),
        facts.collectedMinor(),
        facts.refundedMinor(),
        projectionStatus.name());
  }

  private static FinancialStatus financialStatus(String value) {
    if (value == null) {
      throw review(Conflict.INVOICE_FINANCIAL_CONFLICT);
    }
    try {
      return FinancialStatus.valueOf(value);
    } catch (IllegalArgumentException unknown) {
      throw review(Conflict.INVOICE_FINANCIAL_CONFLICT);
    }
  }

  private static void requireCompatibleInvoice(InvoiceSnapshot invoice, FinancialStatus status) {
    PaymentProjection current = invoice.paymentProjection();
    if (status == FinancialStatus.UNPAID) {
      if (current != null) {
        throw review(Conflict.INVOICE_FINANCIAL_CONFLICT);
      }
      return;
    }
    if (current == null
        || !Objects.equals(current.status(), invoice.financialStatus())
        || !Objects.equals(current.paymentIntentId(), invoice.paymentIntentId())
        || !Objects.equals(current.currency(), invoice.currency())
        || current.amountCollectedMinor() != invoice.expectedAmountMinor()) {
      throw review(Conflict.INVOICE_FINANCIAL_CONFLICT);
    }
    if ((status == FinancialStatus.PAID && current.amountRefundedMinor() != 0)
        || (status == FinancialStatus.REFUNDED
            && current.amountRefundedMinor() != current.amountCollectedMinor())) {
      throw review(Conflict.INVOICE_FINANCIAL_CONFLICT);
    }
  }

  private static void requireCompatibleCollection(PaymentFacts facts, InvoiceSnapshot invoice) {
    if (facts.requestedAmountMinor() <= 0
        || facts.collectedMinor() != invoice.expectedAmountMinor()
        || facts.requestedAmountMinor() != facts.collectedMinor()
        || invoice.expectedAmountMinor() <= 0) {
      throw review(Conflict.AMOUNT_MISMATCH);
    }
    if (!facts.currency().matches("[A-Z]{3}") || !facts.currency().equals(invoice.currency())) {
      throw review(Conflict.CURRENCY_MISMATCH);
    }
    if (!Set.of("automatic", "automatic_async")
        .contains(facts.captureMethod() == null ? "" : facts.captureMethod())) {
      throw review(Conflict.UNSUPPORTED_CAPTURE);
    }
  }

  private static FinancialStatus refundedStatus(PaymentFacts facts) {
    if (facts.refundedMinor() < 0 || facts.refundedMinor() > facts.collectedMinor()) {
      throw review(Conflict.INVALID_REFUND_TOTAL);
    }
    if (facts.refundedMinor() > 0 && facts.refundedMinor() < facts.collectedMinor()) {
      throw review(Conflict.PARTIAL_REFUND);
    }
    return facts.refundedMinor() == 0 ? FinancialStatus.PAID : FinancialStatus.REFUNDED;
  }

  public static String operationKey(String invoiceId, PaymentProjection projection) {
    // Preserve the v1 fingerprint: equivalent observations retain the same ERP
    // operation identity after this refactor, independently of delivery source.
    String canonical =
        "v1|"
            + invoiceId.length()
            + ":"
            + invoiceId
            + "|"
            + projection.paymentIntentId().length()
            + ":"
            + projection.paymentIntentId()
            + "|"
            + projection.currency()
            + "|"
            + projection.amountCollectedMinor()
            + "|"
            + projection.amountRefundedMinor();
    try {
      return "sync-v1-"
          + HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static SyncFailure review(Conflict conflict) {
    return new SyncFailure(WorkState.NEEDS_REVIEW, conflict.name());
  }

  private PaymentPolicy() {}
}
