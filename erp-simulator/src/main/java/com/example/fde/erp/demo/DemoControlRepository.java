package com.example.fde.erp.demo;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

interface DemoControlRepository extends Repository<DemoControl, Long> {
  Optional<DemoControl> findById(Long id);

  @Transactional(propagation = Propagation.MANDATORY)
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select control from DemoControl control where control.id = :id")
  Optional<DemoControl> findForUpdate(@Param("id") long id);
}
