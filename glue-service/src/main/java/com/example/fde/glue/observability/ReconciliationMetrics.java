package com.example.fde.glue.observability;

import com.example.fde.glue.reconciliation.ReconciliationState;
import com.example.fde.glue.reconciliation.ReconciliationStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
class ReconciliationMetrics {
  ReconciliationMetrics(ReconciliationStore store, MeterRegistry metrics) {
    Gauge.builder("sync.reconciliation.last.outcome", store, value -> encode(value.currentState()))
        .register(metrics);
  }

  private static double encode(ReconciliationState state) {
    if (state == null) {
      return 0;
    }
    return switch (state) {
      case COMPLETE -> 1;
      case FAILED -> -1;
      default -> 0;
    };
  }
}
