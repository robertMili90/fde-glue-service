package com.example.fde.erp.invoice;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyResultRepository extends Repository<IdempotencyResult, String> {
  Optional<IdempotencyResult> findById(String operationKey);

  IdempotencyResult save(IdempotencyResult result);

  @Transactional(propagation = Propagation.MANDATORY)
  @Modifying
  @Query("delete from IdempotencyResult")
  void deleteAllForDemoReset();
}
