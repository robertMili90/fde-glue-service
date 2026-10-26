package com.example.fde.erp.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fde.erp.demo.DemoControls;
import com.example.fde.erp.demo.DemoState;
import com.example.fde.erp.demo.FaultRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:erp_repository;DB_CLOSE_DELAY=-1",
      "logging.level.root=WARN"
    })
class ErpPersistenceTest {
  private static final PaymentProjection PAID =
      new PaymentProjection("pi_persistence", "USD", 1200L, 0L, "PAID");
  private final ErpStore invoices;
  private final DemoControls controls;
  private final IdempotencyResultRepository idempotencyResults;
  private final JdbcTemplate jdbc;

  @Autowired
  ErpPersistenceTest(
      ErpStore invoices,
      DemoControls controls,
      IdempotencyResultRepository idempotencyResults,
      JdbcTemplate jdbc) {
    this.invoices = invoices;
    this.controls = controls;
    this.idempotencyResults = idempotencyResults;
    this.jdbc = jdbc;
  }

  @BeforeEach
  void createInvoice() {
    controls.reset();
    assertThat(
            invoices
                .create(new CreateInvoice("inv_persistence", 1200L, "USD", "pi_persistence"))
                .status())
        .isEqualTo(201);
  }

  @Test
  void assignedOperationKeyCannotOverwriteACommittedIdempotencyResult() {
    InvoiceResult committed = invoices.project("inv_persistence", "original", 1, PAID);
    assertThat(committed.status()).isEqualTo(200);
    assertThatThrownBy(
            () -> idempotencyResults.save(new IdempotencyResult("original", "changed", "changed")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(invoices.project("inv_persistence", "original", 1, PAID)).isEqualTo(committed);
    assertThat(controls.state()).isEqualTo(new DemoState(1, 1, 1));
  }

  @Test
  void idempotencyInsertFailureRollsBackProjectionAndFaultCountersAndAllowsRetry() {
    controls.configure(new FaultRequest("APPLY_THEN_DELAY", 1, 1));
    jdbc.execute(
        """
        alter table erp.idempotency_result add constraint reject_operation
        check (operation_key <> 'rollback')
        """);
    try {
      assertThatThrownBy(() -> invoices.project("inv_persistence", "rollback", 1, PAID))
          .isInstanceOf(DataIntegrityViolationException.class);
      InvoiceView unchanged = (InvoiceView) invoices.get("inv_persistence").body();
      assertThat(unchanged.financialStatus()).isEqualTo(FinancialStatus.UNPAID);
      assertThat(unchanged.version()).isEqualTo(1);
      assertThat(unchanged.paymentProjection()).isNull();
      assertThat(controls.state()).isEqualTo(new DemoState(1, 0, 0));
      assertThat(idempotencyResults.findById("rollback")).isEmpty();
      assertThat(
              jdbc.queryForObject(
                  "select remaining_requests from erp.demo_control where id = 1", Integer.class))
          .isEqualTo(1);
    } finally {
      jdbc.execute("alter table erp.idempotency_result drop constraint reject_operation");
    }

    InvoiceResult retried = invoices.project("inv_persistence", "rollback", 1, PAID);
    assertThat(retried.status()).isEqualTo(200);
    assertThat(retried.delayMs()).isEqualTo(1);
    assertThat(controls.state()).isEqualTo(new DemoState(1, 1, 1));
    InvoiceResult replayed = invoices.project("inv_persistence", "rollback", 1, PAID);
    assertThat(replayed.body()).isEqualTo(retried.body());
    assertThat(replayed.delayMs()).isZero();
    assertThat(controls.state()).isEqualTo(new DemoState(1, 1, 1));
  }
}
