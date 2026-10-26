package com.example.fde.glue.work;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fde.glue.erp.InvoiceSnapshot;
import com.example.fde.glue.erp.PaymentProjection;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyncPolicyTest {
  private final PaymentFacts paid =
      new PaymentFacts("pi_policy", "succeeded", "JPY", 1200, 1200, 0, false, "automatic");

  @Test
  void incompatibleExistingInvoiceFactsAreHeldBeforeAnyNoOpOrWrite() {
    var projection = new PaymentProjection("pi_policy", "JPY", 1200, 0, "PAID");
    var invalid =
        List.of(
            new InvoiceSnapshot("i", 1200, "JPY", "pi_policy", 1, "UNPAID", projection),
            new InvoiceSnapshot("i", 1200, "JPY", "pi_policy", 1, "PAID", null),
            new InvoiceSnapshot("i", 1200, "JPY", "pi_policy", 1, "REFUNDED", projection),
            new InvoiceSnapshot(
                "i",
                1200,
                "JPY",
                "pi_policy",
                1,
                "PAID",
                new PaymentProjection("pi_other", "JPY", 1200, 0, "PAID")),
            new InvoiceSnapshot(
                "i",
                1200,
                "JPY",
                "pi_policy",
                1,
                "PAID",
                new PaymentProjection("pi_policy", "USD", 1200, 0, "PAID")),
            new InvoiceSnapshot(
                "i",
                1200,
                "JPY",
                "pi_policy",
                1,
                "PAID",
                new PaymentProjection("pi_policy", "JPY", 900, 0, "PAID")));
    for (var invoice : invalid) {
      assertThatThrownBy(() -> PaymentPolicy.normalize(paid, invoice))
          .isInstanceOf(SyncFailure.class)
          .hasMessage("INVOICE_FINANCIAL_CONFLICT");
    }
  }

  @Test
  void minorUnitsRemainIntegralAtWideBoundsAndRefundChangesOperationIdentity() {
    var invoice = new InvoiceSnapshot("i", Long.MAX_VALUE, "JPY", "pi_policy", 1, "UNPAID", null);
    var collected =
        new PaymentFacts(
            "pi_policy", "succeeded", "JPY", Long.MAX_VALUE, Long.MAX_VALUE, 0, false, "automatic");
    var refunded =
        new PaymentFacts(
            "pi_policy",
            "succeeded",
            "JPY",
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            Long.MAX_VALUE,
            false,
            "automatic");
    var first = PaymentPolicy.normalize(collected, invoice);
    var second = PaymentPolicy.normalize(refunded, invoice);
    assertThat(first.amountCollectedMinor()).isEqualTo(Long.MAX_VALUE);
    assertThat(second.amountRefundedMinor()).isEqualTo(Long.MAX_VALUE);
    assertThat(second.status()).isEqualTo("REFUNDED");
    assertThat(PaymentPolicy.operationKey("i", first))
        .isNotEqualTo(PaymentPolicy.operationKey("i", second));
  }
}
