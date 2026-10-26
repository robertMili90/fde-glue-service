package com.example.fde.glue.work;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface WorkItemRepository extends Repository<WorkItem, String> {
  Optional<WorkItem> findById(String paymentIntentId);

  WorkItem save(WorkItem workItem);

  List<WorkItem> findByState(WorkState state);

  @Query(
      """
      select work from WorkItem work
      where work.state in :states and work.nextEligibleAt <= :now
      order by work.nextEligibleAt, work.createdAt, work.paymentIntentId
      """)
  List<WorkItem> findEligible(
      @Param("states") Collection<WorkState> states, @Param("now") Instant now, Pageable pageable);

  List<WorkItem> findByPaymentIntentIdGreaterThanOrderByPaymentIntentId(
      String after, Pageable pageable);

  long countByState(WorkState state);

  @Query("select min(work.pendingSince) from WorkItem work where work.state in :states")
  Optional<Instant> findOldestPendingSince(@Param("states") Collection<WorkState> states);
}
