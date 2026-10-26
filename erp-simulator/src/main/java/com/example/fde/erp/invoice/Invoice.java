package com.example.fde.erp.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "invoice", schema = "erp")
public class Invoice implements Persistable<String> {
  // Assigned IDs must still use insert semantics for new instances.
  @Transient private boolean newEntity = true;

  @Id
  @Column(length = 255)
  private String id;

  @Column(nullable = false, unique = true, length = 255)
  private String paymentIntentId;

  @Column(nullable = false)
  private long expectedAmountMinor;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false)
  private long version;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 16)
  private FinancialStatus financialStatus;

  private Long amountCollectedMinor;
  private Long amountRefundedMinor;

  protected Invoice() {}

  Invoice(CreateInvoice request) {
    id = request.id();
    paymentIntentId = request.paymentIntentId();
    expectedAmountMinor = request.expectedAmountMinor();
    currency = request.currency();
    version = 1;
    financialStatus = FinancialStatus.UNPAID;
  }

  String id() {
    return id;
  }

  boolean hasVersion(long expectedVersion) {
    return version == expectedVersion;
  }

  void simulateConcurrentChange() {
    version++;
  }

  boolean compatible(PaymentProjection projection) {
    boolean paid =
        FinancialStatus.PAID.name().equals(projection.status())
            && projection.amountRefundedMinor() == 0;
    boolean refunded =
        FinancialStatus.REFUNDED.name().equals(projection.status())
            && projection.amountRefundedMinor().equals(projection.amountCollectedMinor());
    return paymentIntentId.equals(projection.paymentIntentId())
        && currency.equals(projection.currency())
        && expectedAmountMinor == projection.amountCollectedMinor()
        && (paid || refunded)
        && (financialStatus != FinancialStatus.REFUNDED || refunded);
  }

  boolean project(PaymentProjection projection) {
    if (!compatible(projection)) {
      throw new IllegalArgumentException("Incompatible financial projection");
    }
    if (projection.equals(view().paymentProjection())) {
      return false;
    }
    amountCollectedMinor = projection.amountCollectedMinor();
    amountRefundedMinor = projection.amountRefundedMinor();
    financialStatus = FinancialStatus.valueOf(projection.status());
    version++;
    return true;
  }

  InvoiceView view() {
    PaymentProjection projection = null;
    if (amountCollectedMinor != null) {
      projection =
          new PaymentProjection(
              paymentIntentId,
              currency,
              amountCollectedMinor,
              amountRefundedMinor,
              financialStatus.name());
    }
    return new InvoiceView(
        id, expectedAmountMinor, currency, paymentIntentId, version, financialStatus, projection);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  private void markPersisted() {
    newEntity = false;
  }
}
