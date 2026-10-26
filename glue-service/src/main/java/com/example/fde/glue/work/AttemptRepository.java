package com.example.fde.glue.work;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

interface AttemptRepository extends Repository<Attempt, String> {
  Optional<Attempt> findById(String attemptId);

  Attempt save(Attempt attempt);

  List<Attempt> findByOutcome(AttemptOutcome outcome);

  List<Attempt> findByPaymentIntentIdOrderByStartedAtDescAttemptNumberDesc(
      String paymentIntentId, Pageable pageable);
}
