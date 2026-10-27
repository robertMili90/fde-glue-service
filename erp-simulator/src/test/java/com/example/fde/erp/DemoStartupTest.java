package com.example.fde.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fde.erp.demo.DemoControls;
import com.example.fde.erp.demo.DemoInvoiceSeeder;
import com.example.fde.erp.demo.DemoState;
import com.example.fde.erp.demo.FaultRequest;
import com.example.fde.erp.invoice.CreateInvoice;
import com.example.fde.erp.invoice.ErpStore;
import com.example.fde.erp.invoice.FinancialStatus;
import com.example.fde.erp.invoice.InvoiceView;
import com.example.fde.erp.invoice.PaymentProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "spring.datasource.url=jdbc:h2:mem:erp_demo_startup;DB_CLOSE_DELAY=0",
      "logging.level.root=WARN"
    })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DemoStartupTest {
  @Autowired ErpStore invoices;
  @Autowired DemoControls controls;
  @Autowired DemoInvoiceSeeder seeder;

  @Test
  void startupSeedsFourInvoicesAndRepeatingItPreservesEditsAndOtherInvoices() {
    assertThat(controls.state()).isEqualTo(new DemoState(4, 2, 2));
    assertThat(((InvoiceView) invoices.get("inv_demo").body()).financialStatus())
        .isEqualTo(FinancialStatus.PAID);
    assertThat(((InvoiceView) invoices.get("inv_refunded").body()).financialStatus())
        .isEqualTo(FinancialStatus.REFUNDED);
    assertThat(invoices.create(new CreateInvoice("user_invoice", 2500L, "EUR", "pi_user")).status())
        .isEqualTo(201);
    assertThat(
            invoices
                .project(
                    "inv_demo",
                    "operator_refund",
                    2,
                    new PaymentProjection("pi_demo", "USD", 1200L, 1200L, "REFUNDED"))
                .status())
        .isEqualTo(200);

    seeder.run(new DefaultApplicationArguments());
    assertThat(controls.state()).isEqualTo(new DemoState(5, 2, 3));
    assertThat(((InvoiceView) invoices.get("inv_demo").body()).financialStatus())
        .isEqualTo(FinancialStatus.REFUNDED);
    assertThat(invoices.get("user_invoice").status()).isEqualTo(200);
    // Original-key replay still resolves the seeded result even after the operator refund.
    assertThat(
            invoices
                .project(
                    "inv_demo",
                    "demo-paid-v1",
                    1,
                    new PaymentProjection("pi_demo", "USD", 1200L, 0L, "PAID"))
                .status())
        .isEqualTo(200);
    assertThat(controls.state()).isEqualTo(new DemoState(5, 2, 3));
  }

  @Test
  void initializationPreservesAnInjectedFailureForTheNextNormalRequest() {
    controls.reset();
    controls.configure(new FaultRequest("FAIL_BEFORE_WRITE", 1, 0));
    seeder.run(new DefaultApplicationArguments());
    assertThat(controls.state()).isEqualTo(new DemoState(4, 2, 2));
    assertThat(invoices.create(new CreateInvoice("probe", 1200L, "USD", "pi_probe")).status())
        .isEqualTo(201);
    var payment = new PaymentProjection("pi_probe", "USD", 1200L, 0L, "PAID");
    assertThat(invoices.project("probe", "probe_key", 1, payment).status()).isEqualTo(503);
    assertThat(controls.state()).isEqualTo(new DemoState(5, 2, 2));
    assertThat(invoices.project("probe", "probe_key", 1, payment).status()).isEqualTo(200);
    assertThat(controls.state()).isEqualTo(new DemoState(5, 3, 3));
  }

  @Test
  void fixtureIdentityConflictRollsBackAllNewSeedsWithoutOverwritingExistingData() {
    controls.reset();
    assertThat(invoices.create(new CreateInvoice("inv_review", 9999L, "EUR", "pi_other")).status())
        .isEqualTo(201);
    assertThatThrownBy(() -> seeder.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Demo invoice identity conflict: inv_review");
    assertThat(controls.state()).isEqualTo(new DemoState(1, 0, 0));
    assertThat(invoices.get("inv_demo").status()).isEqualTo(404);
    assertThat(((InvoiceView) invoices.get("inv_review").body()).expectedAmountMinor())
        .isEqualTo(9999);
  }
}
