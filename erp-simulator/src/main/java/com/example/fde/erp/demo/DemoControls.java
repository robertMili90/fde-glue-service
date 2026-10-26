package com.example.fde.erp.demo;

import com.example.fde.erp.invoice.IdempotencyResultRepository;
import com.example.fde.erp.invoice.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DemoControls {
  private final DemoControlRepository controls;
  private final InvoiceRepository invoices;
  private final IdempotencyResultRepository idempotencyResults;

  public DemoControls(
      DemoControlRepository controls,
      InvoiceRepository invoices,
      IdempotencyResultRepository idempotencyResults) {
    this.controls = controls;
    this.invoices = invoices;
    this.idempotencyResults = idempotencyResults;
  }

  private DemoControl lockedControl() {
    return controls
        .findForUpdate(1L)
        .orElseThrow(() -> new IllegalStateException("ERP demo control is missing"));
  }

  // Invoice mutation, idempotency and fault counters share this short DB lock.
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockMutations() {
    lockedControl();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public FaultEffect consumeFault() {
    return lockedControl().consumeFault();
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void appliedUpdate() {
    lockedControl().appliedUpdate();
  }

  public void configure(FaultRequest request) {
    lockedControl().configure(request);
  }

  @Transactional(readOnly = true)
  public DemoState state() {
    DemoControl control =
        controls
            .findById(1L)
            .orElseThrow(() -> new IllegalStateException("ERP demo control is missing"));
    return new DemoState(
        invoices.count(),
        invoices.countByAmountCollectedMinorIsNotNull(),
        control.appliedUpdateCount());
  }

  public void reset() {
    DemoControl control = lockedControl();
    idempotencyResults.deleteAllForDemoReset();
    invoices.deleteAllForDemoReset();
    control.reset();
  }
}
