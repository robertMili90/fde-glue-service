package com.example.fde.glue.reconciliation;

import com.example.fde.glue.config.SyncSettings;
import com.example.fde.glue.work.SyncFailure;
import com.example.fde.glue.work.WorkLock;
import com.example.fde.glue.work.WorkState;
import com.example.fde.glue.work.WorkStore;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReconciliationStore {
  private final ReconciliationRunRepository runs;
  private final Clock clock;
  private final WorkStore workStore;
  private final WorkLock workLock;
  private final SyncSettings settings;

  public ReconciliationStore(
      Clock clock,
      WorkStore workStore,
      WorkLock workLock,
      SyncSettings settings,
      ReconciliationRunRepository runs) {
    this.runs = runs;
    this.clock = clock;
    this.workStore = workStore;
    this.workLock = workLock;
    this.settings = settings;
  }

  public ReconciliationRunView request() {
    String currentRunId = workLock.lock();
    ReconciliationRun reconciliationRun = currentRunId == null ? null : requireRun(currentRunId);
    if (reconciliationRun == null || reconciliationRun.complete()) {
      reconciliationRun = new ReconciliationRun(UUID.randomUUID().toString(), clock.instant());
      runs.save(reconciliationRun);
      workLock.selectReconciliation(reconciliationRun.id());
    } else {
      reconciliationRun.request(clock.instant());
    }
    return reconciliationRun.view();
  }

  public ReconciliationRunView claim() {
    String currentRunId = workLock.lock();
    if (currentRunId == null) {
      return null;
    }
    ReconciliationRun reconciliationRun = requireRun(currentRunId);
    return reconciliationRun.claim(clock.instant()) ? reconciliationRun.view() : null;
  }

  public ReconciliationRunView page(String reconciliationRunId, LinkedInvoicePage page) {
    ReconciliationRun reconciliationRun = lockRun(reconciliationRunId);
    reconciliationRun.requireRunning();
    validatePage(reconciliationRun, page);
    for (LinkedInvoicePage.Reference reference : page.items()) {
      workStore.enqueue(reference.paymentIntentId());
    }
    // Enqueues join this transaction. A validation/DB failure rolls back every
    // reference and the checkpoint; no HTTP call occurs while holding the gate.
    reconciliationRun.checkpoint(page.items().size(), page.nextCursor(), clock.instant());
    return reconciliationRun.view();
  }

  private void validatePage(ReconciliationRun reconciliationRun, LinkedInvoicePage page) {
    if (page == null
        || page.items() == null
        || page.items().size() > settings.reconciliationPageSize()) {
      throw invalid(ReconciliationReason.ERP_INVALID_PAGE);
    }
    if (reconciliationRun.pagesProcessed() >= settings.reconciliationMaxPages()) {
      throw invalid(ReconciliationReason.ERP_ENUMERATION_LIMIT);
    }
    String nextCursor = page.nextCursor();
    if (nextCursor != null
        && (page.items().isEmpty()
            || nextCursor.isEmpty()
            || nextCursor.length() > 255
            || Objects.equals(nextCursor, reconciliationRun.cursor()))) {
      throw invalid(ReconciliationReason.ERP_INVALID_CURSOR);
    }
    Set<String> invoiceIds = new HashSet<>();
    for (LinkedInvoicePage.Reference reference : page.items()) {
      if (reference == null
          || reference.invoiceId() == null
          || !reference.invoiceId().matches("[A-Za-z0-9_-]+")
          || reference.invoiceId().length() > 255
          || !invoiceIds.add(reference.invoiceId())
          || reference.paymentIntentId() == null
          || !reference.paymentIntentId().matches("pi_[A-Za-z0-9_]+")
          || reference.paymentIntentId().length() > 255) {
        throw invalid(ReconciliationReason.ERP_INVALID_REFERENCE);
      }
    }
  }

  private SyncFailure invalid(ReconciliationReason reason) {
    return new SyncFailure(WorkState.FAILED, reason.name());
  }

  private ReconciliationRun lockRun(String reconciliationRunId) {
    String currentRunId = workLock.lock();
    if (!Objects.equals(currentRunId, reconciliationRunId)) {
      throw new IllegalStateException("Reconciliation run is not the current owner");
    }
    return requireRun(reconciliationRunId);
  }

  private ReconciliationRun requireRun(String reconciliationRunId) {
    ReconciliationRun reconciliationRun = runs.findById(reconciliationRunId).orElse(null);
    if (reconciliationRun == null) {
      throw new IllegalStateException("Reconciliation run is missing: " + reconciliationRunId);
    }
    return reconciliationRun;
  }

  public void pause(String reconciliationRunId) {
    lockRun(reconciliationRunId).pause(clock.instant());
  }

  public void fail(String reconciliationRunId, String reasonCode) {
    lockRun(reconciliationRunId).fail(reasonCode, clock.instant());
  }

  public void recover() {
    workLock.lock();
    for (ReconciliationRun reconciliationRun : runs.findByState(ReconciliationState.RUNNING)) {
      reconciliationRun.pause(clock.instant());
    }
  }

  @Transactional(readOnly = true)
  public ReconciliationRunView find(String reconciliationRunId) {
    ReconciliationRun reconciliationRun = runs.findById(reconciliationRunId).orElse(null);
    return reconciliationRun == null ? null : reconciliationRun.view();
  }

  @Transactional(readOnly = true)
  public List<ReconciliationRunView> list(int limit) {
    return runs.findAllByOrderByStartedAtDescIdAsc(PageRequest.of(0, limit)).stream()
        .map(ReconciliationRun::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public ReconciliationState currentState() {
    String currentRunId = workLock.currentReconciliationId();
    return currentRunId == null ? null : requireRun(currentRunId).state();
  }
}
