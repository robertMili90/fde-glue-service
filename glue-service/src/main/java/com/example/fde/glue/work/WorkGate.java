package com.example.fde.glue.work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "work_gate", schema = "glue")
class WorkGate {
  @Id private long id;

  @Column(length = 36)
  private String currentScanId;

  protected WorkGate() {}

  String currentScanId() {
    return currentScanId;
  }

  void selectReconciliation(String reconciliationRunId) {
    currentScanId = reconciliationRunId;
  }
}
