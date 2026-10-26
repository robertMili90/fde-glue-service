package com.example.fde.glue.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fde.glue.work.WorkState;
import com.example.fde.glue.work.WorkStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:reconciliation_atomicity;DB_CLOSE_DELAY=-1",
      "sync.enabled=false",
      "sync.reconciliation-page-size=2",
      "stripe.secret-key=",
      "stripe.webhook-secret=",
      "logging.level.root=WARN"
    })
class ReconciliationPersistenceTest {
  private final ReconciliationStore scans;
  private final WorkStore work;
  private final JdbcTemplate jdbc;

  @Autowired
  ReconciliationPersistenceTest(ReconciliationStore scans, WorkStore work, JdbcTemplate jdbc) {
    this.scans = scans;
    this.work = work;
    this.jdbc = jdbc;
  }

  @Test
  void secondEnqueueFailureRollsBackTheWholePageAndAllowsSafeReplay() {
    var reconciliationRun = scans.request();
    scans.claim();
    var page =
        new LinkedInvoicePage(
            List.of(
                new LinkedInvoicePage.Reference("z_first", "pi_atomic_first"),
                new LinkedInvoicePage.Reference("a_second", "pi_atomic_reject")),
            "opaque_checkpoint");
    jdbc.execute(
        "ALTER TABLE glue.work_item ADD CONSTRAINT reject_second CHECK(payment_intent_id <> 'pi_atomic_reject')");
    try {
      assertThatThrownBy(() -> scans.page(reconciliationRun.id(), page))
          .isInstanceOf(RuntimeException.class);
      assertThat(work.find("pi_atomic_first")).isNull();
      assertThat(work.find("pi_atomic_reject")).isNull();
      var unchanged = scans.find(reconciliationRun.id());
      assertThat(unchanged.state()).isEqualTo(ReconciliationState.RUNNING);
      assertThat(unchanged.cursor()).isNull();
      assertThat(unchanged.pagesProcessed()).isZero();
      assertThat(unchanged.enqueuedReferences()).isZero();
    } finally {
      jdbc.execute("ALTER TABLE glue.work_item DROP CONSTRAINT reject_second");
    }
    var checkpoint = scans.page(reconciliationRun.id(), page);
    assertThat(checkpoint.cursor()).isEqualTo("opaque_checkpoint");
    assertThat(checkpoint.pagesProcessed()).isEqualTo(1);
    assertThat(checkpoint.enqueuedReferences()).isEqualTo(2);
    assertThat(work.find("pi_atomic_first").state()).isEqualTo(WorkState.READY);
    assertThat(work.find("pi_atomic_reject").state()).isEqualTo(WorkState.READY);
  }
}
