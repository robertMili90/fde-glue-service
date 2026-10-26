package com.example.fde.glue.reconciliation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

interface ReconciliationRunRepository extends Repository<ReconciliationRun, String> {
  Optional<ReconciliationRun> findById(String reconciliationRunId);

  ReconciliationRun save(ReconciliationRun reconciliationRun);

  List<ReconciliationRun> findByState(ReconciliationState state);

  List<ReconciliationRun> findAllByOrderByStartedAtDescIdAsc(Pageable pageable);
}
