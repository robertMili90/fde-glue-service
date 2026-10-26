package com.example.fde.glue.reconciliation;

import com.example.fde.glue.config.SyncSettings;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ReconciliationLifecycle {
  private final ReconciliationStore store;
  private final Reconciler reconciler;
  private final SyncSettings settings;

  ReconciliationLifecycle(ReconciliationStore store, Reconciler reconciler, SyncSettings settings) {
    this.store = store;
    this.reconciler = reconciler;
    this.settings = settings;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    store.recover();
  }

  @Scheduled(
      fixedDelayString = "${sync.reconciliation-ms:60000}",
      initialDelayString = "${sync.reconciliation-initial-ms:5000}")
  void requestScan() {
    if (settings.enabled()) {
      store.request();
    }
  }

  @Scheduled(
      fixedDelayString = "${sync.reconciliation-poll-ms:500}",
      initialDelayString = "${sync.initial-delay-ms:1000}")
  void processBatch() {
    if (settings.enabled()) {
      reconciler.tick();
    }
  }
}
