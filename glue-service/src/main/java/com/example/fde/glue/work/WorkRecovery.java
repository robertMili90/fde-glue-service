package com.example.fde.glue.work;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class WorkRecovery {
  private final WorkStore workStore;

  WorkRecovery(WorkStore workStore) {
    this.workStore = workStore;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    workStore.recover();
  }
}
