package com.example.fde.glue.work;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

interface WorkGateRepository extends Repository<WorkGate, Long> {
  Optional<WorkGate> findById(Long id);

  // The caller owns the transaction so this lock spans all related repository writes.
  @Transactional(propagation = Propagation.MANDATORY)
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select gate from WorkGate gate where gate.id = :id")
  Optional<WorkGate> findForUpdate(@Param("id") long id);
}
