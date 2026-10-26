package com.example.fde.glue.work;

import com.example.fde.glue.erp.ErpClient;
import com.example.fde.glue.erp.InvoiceSnapshot;
import com.example.fde.glue.erp.PaymentProjection;
import com.example.fde.glue.observability.FailureDiagnostics;
import com.example.fde.glue.stripe.StripeReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SyncEngine {
  private static final Logger log = LoggerFactory.getLogger(SyncEngine.class);
  private static final int MAX_VERSION_ATTEMPTS = 3;

  private enum Reason {
    PAYMENT_NOT_SUCCEEDED,
    REFUND_PENDING,
    SYNCHRONIZED,
    ERP_VERSION_CONTENTION
  }

  private final StripeReader stripe;
  private final ErpClient erp;
  private final ObjectMapper json;

  public SyncEngine(StripeReader stripe, ErpClient erp, ObjectMapper json) {
    this.stripe = stripe;
    this.erp = erp;
    this.json = json;
  }

  public WorkStore.Outcome sync(String paymentIntentId) {
    PaymentFacts facts = stripe.read(paymentIntentId);
    InvoiceSnapshot invoice = null;
    String operationKey = null;
    try {
      for (int versionAttempt = 0; versionAttempt < MAX_VERSION_ATTEMPTS; versionAttempt++) {
        invoice = erp.find(paymentIntentId);
        PaymentPolicy.requireAssociation(paymentIntentId, invoice);
        PaymentPolicy.PaymentStage paymentStage = PaymentPolicy.paymentStage(facts.status());
        if (paymentStage == PaymentPolicy.PaymentStage.WAITING) {
          return new WorkStore.Outcome(
              WorkState.WAITING_PROVIDER,
              Reason.PAYMENT_NOT_SUCCEEDED.name(),
              invoice.id(),
              null,
              encode(facts),
              0);
        }
        if (paymentStage == PaymentPolicy.PaymentStage.CANCELED) {
          return new WorkStore.Outcome(
              WorkState.DONE, WorkReason.PAYMENT_CANCELED, invoice.id(), null, encode(facts), 0);
        }
        PaymentProjection projection = PaymentPolicy.normalize(facts, invoice);
        operationKey = PaymentPolicy.operationKey(invoice.id(), projection);
        if (projection.equals(invoice.paymentProjection())
            || erp.project(invoice, operationKey, projection)) {
          WorkState state = facts.pendingRefund() ? WorkState.WAITING_PROVIDER : WorkState.DONE;
          String reasonCode =
              facts.pendingRefund() ? Reason.REFUND_PENDING.name() : Reason.SYNCHRONIZED.name();
          return new WorkStore.Outcome(
              state, reasonCode, invoice.id(), operationKey, encode(facts), 0);
        }
      }
      throw new SyncFailure(WorkState.RETRY_SCHEDULED, Reason.ERP_VERSION_CONTENTION.name());
    } catch (SyncFailure failure) {
      log.warn(
          "sync observation failed; payment={} invoice={} operation={} reason={} diagnostic={}",
          paymentIntentId,
          invoice == null ? null : invoice.id(),
          operationKey,
          failure.reason(),
          FailureDiagnostics.describe(failure));
      return new WorkStore.Outcome(
          failure.state(),
          failure.reason(),
          invoice == null ? null : invoice.id(),
          operationKey,
          encode(facts),
          failure.retryAfterMs());
    }
  }

  private String encode(PaymentFacts facts) {
    try {
      return json.writeValueAsString(facts);
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Cannot encode observed payment facts", failure);
    }
  }
}
