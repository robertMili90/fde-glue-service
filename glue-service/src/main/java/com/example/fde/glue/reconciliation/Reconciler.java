package com.example.fde.glue.reconciliation;

import com.example.fde.glue.config.SyncSettings;
import com.example.fde.glue.erp.ErpClient;
import com.example.fde.glue.erp.InvoicePage;
import com.example.fde.glue.observability.FailureDiagnostics;
import com.example.fde.glue.work.SyncFailure;
import com.example.fde.glue.work.WorkState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Reconciler {
  private static final Logger log = LoggerFactory.getLogger(Reconciler.class);
  private final ReconciliationStore store;
  private final ErpClient erp;
  private final SyncSettings settings;

  public Reconciler(ReconciliationStore store, ErpClient erp, SyncSettings settings) {
    this.store = store;
    this.erp = erp;
    this.settings = settings;
  }

  public synchronized void tick() {
    ReconciliationRunView reconciliationRun = null;
    try {
      reconciliationRun = store.claim();
      if (reconciliationRun == null) {
        return;
      }
      for (int pageNumber = 0; pageNumber < settings.reconciliationBatchPages(); pageNumber++) {
        if (reconciliationRun.pagesProcessed() >= settings.reconciliationMaxPages()) {
          throw new SyncFailure(
              WorkState.FAILED, ReconciliationReason.ERP_ENUMERATION_LIMIT.name());
        }
        InvoicePage page = erp.list(reconciliationRun.cursor(), settings.reconciliationPageSize());
        LinkedInvoicePage references =
            new LinkedInvoicePage(
                page.items().stream()
                    .map(
                        invoice ->
                            invoice == null
                                ? null
                                : new LinkedInvoicePage.Reference(
                                    invoice.id(), invoice.paymentIntentId()))
                    .toList(),
                page.nextCursor());
        reconciliationRun = store.page(reconciliationRun.id(), references);
        if (reconciliationRun.state() == ReconciliationState.COMPLETE) {
          log.info(
              "reconciliation run={} state=COMPLETE references={}",
              reconciliationRun.id(),
              reconciliationRun.enqueuedReferences());
          return;
        }
      }
      store.pause(reconciliationRun.id());
      log.info(
          "reconciliation run={} state=INCOMPLETE cursor={}",
          reconciliationRun.id(),
          reconciliationRun.cursor());
    } catch (RuntimeException failure) {
      String reasonCode =
          failure instanceof SyncFailure syncFailure
              ? syncFailure.reason()
              : ReconciliationReason.RECONCILIATION_ERROR.name();
      if (reconciliationRun != null) {
        try {
          store.fail(reconciliationRun.id(), reasonCode);
        } catch (RuntimeException persistence) {
          log.error(
              "reconciliation failure could not be persisted; run={} diagnostic={}",
              reconciliationRun.id(),
              FailureDiagnostics.describe(persistence));
        }
      }
      log.warn(
          "reconciliation run={} failed cursor={} reason={} diagnostic={}",
          reconciliationRun == null ? null : reconciliationRun.id(),
          reconciliationRun == null ? null : reconciliationRun.cursor(),
          reasonCode,
          FailureDiagnostics.describe(failure));
    }
  }
}
