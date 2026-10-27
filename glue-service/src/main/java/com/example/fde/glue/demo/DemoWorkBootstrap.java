package com.example.fde.glue.demo;

import com.example.fde.glue.reconciliation.ReconciliationStore;
import com.example.fde.glue.work.WorkStore;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
class DemoWorkBootstrap implements ApplicationRunner {
  private final WorkStore work;
  private final ReconciliationStore reconciliations;

  DemoWorkBootstrap(WorkStore work, ReconciliationStore reconciliations) {
    this.work = work;
    this.reconciliations = reconciliations;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    // Queue ordinary durable observations. Existing operator holds/retry budgets remain intact.
    for (String paymentIntentId : List.of("pi_demo", "pi_refunded", "pi_review", "pi_unpaid")) {
      work.enqueue(paymentIntentId);
    }
    reconciliations.request();
  }
}
