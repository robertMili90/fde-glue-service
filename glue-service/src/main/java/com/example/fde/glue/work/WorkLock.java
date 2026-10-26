package com.example.fde.glue.work;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class WorkLock {
  private final WorkGateRepository gates;

  public WorkLock(WorkGateRepository gates) {
    this.gates = gates;
  }

  // Every local writer shares this gate. In particular, a scan page and its
  // enqueues use the same transaction/lock order as concurrent webhook receipts.
  public String lock() {
    WorkGate gate =
        gates
            .findForUpdate(1L)
            .orElseThrow(() -> new IllegalStateException("Durable work gate is missing"));
    return gate.currentScanId();
  }

  public void selectReconciliation(String reconciliationRunId) {
    requireGate().selectReconciliation(reconciliationRunId);
  }

  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public String currentReconciliationId() {
    return requireGate().currentScanId();
  }

  private WorkGate requireGate() {
    return gates
        .findById(1L)
        .orElseThrow(() -> new IllegalStateException("Durable work gate is missing"));
  }
}
