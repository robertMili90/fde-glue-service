package com.example.fde.erp.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "demo_control", schema = "erp")
class DemoControl {
  @Id private long id;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 32)
  private FaultMode mode;

  @Column(nullable = false)
  private int remainingRequests;

  @Column(nullable = false)
  private int delayMs;

  @Column(nullable = false)
  private long appliedUpdateCount;

  protected DemoControl() {}

  FaultEffect consumeFault() {
    if (remainingRequests <= 0) {
      return new FaultEffect(FaultMode.NONE, 0);
    }
    remainingRequests--;
    return new FaultEffect(mode, delayMs);
  }

  void configure(FaultRequest request) {
    mode = FaultMode.valueOf(request.mode());
    if (mode == FaultMode.NONE) {
      remainingRequests = 0;
    } else if (request.remainingRequests() == null) {
      remainingRequests = 1;
    } else {
      remainingRequests = request.remainingRequests();
    }
    delayMs = request.delayMs() == null ? 0 : request.delayMs();
  }

  void appliedUpdate() {
    appliedUpdateCount++;
  }

  long appliedUpdateCount() {
    return appliedUpdateCount;
  }

  void reset() {
    mode = FaultMode.NONE;
    remainingRequests = 0;
    delayMs = 0;
    appliedUpdateCount = 0;
  }
}
