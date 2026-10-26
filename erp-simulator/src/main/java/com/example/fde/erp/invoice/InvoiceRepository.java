package com.example.fde.erp.invoice;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface InvoiceRepository extends Repository<Invoice, String> {
  Optional<Invoice> findById(String invoiceId);

  Invoice save(Invoice invoice);

  boolean existsById(String invoiceId);

  boolean existsByPaymentIntentId(String paymentIntentId);

  Optional<Invoice> findByPaymentIntentId(String paymentIntentId);

  List<Invoice> findByIdGreaterThanOrderById(String cursor, Pageable pageable);

  long count();

  long countByAmountCollectedMinorIsNotNull();

  // Demo reset joins the same transaction as idempotency deletion and counter reset.
  @Transactional(propagation = Propagation.MANDATORY)
  @Modifying
  @Query("delete from Invoice")
  void deleteAllForDemoReset();
}
