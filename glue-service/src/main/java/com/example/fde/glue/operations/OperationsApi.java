package com.example.fde.glue.operations;

import com.example.fde.glue.reconciliation.ReconciliationRunView;
import com.example.fde.glue.reconciliation.ReconciliationStore;
import com.example.fde.glue.work.AttemptView;
import com.example.fde.glue.work.WorkStore;
import com.example.fde.glue.work.WorkView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/operations")
class OperationsApi {
  private final WorkStore workStore;
  private final ReconciliationStore reconciliations;

  OperationsApi(WorkStore workStore, ReconciliationStore reconciliations) {
    this.workStore = workStore;
    this.reconciliations = reconciliations;
  }

  private int checkedLimit(int limit) {
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit must be 1..100");
    }
    return limit;
  }

  @GetMapping("/work")
  List<WorkView> list(
      @RequestParam(defaultValue = "") String after,
      @RequestParam(defaultValue = "100") int limit) {
    return workStore.list(after, checkedLimit(limit));
  }

  @GetMapping("/work/{paymentIntentId}")
  ResponseEntity<WorkView> get(@PathVariable String paymentIntentId) {
    WorkView work = workStore.find(paymentIntentId);
    if (work == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item was not found");
    }
    return ResponseEntity.ok(work);
  }

  @GetMapping("/work/{paymentIntentId}/attempts")
  List<AttemptView> attempts(
      @PathVariable String paymentIntentId, @RequestParam(defaultValue = "100") int limit) {
    return workStore.attempts(paymentIntentId, checkedLimit(limit));
  }

  @PostMapping("/work/{paymentIntentId}/requeue")
  ResponseEntity<WorkView> requeue(@PathVariable String paymentIntentId) {
    if (!workStore.requeue(paymentIntentId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item was not found");
    }
    return ResponseEntity.accepted().body(workStore.find(paymentIntentId));
  }

  @PostMapping("/reconciliations")
  ResponseEntity<ReconciliationRunView> reconcile() {
    return ResponseEntity.accepted().body(reconciliations.request());
  }

  @GetMapping("/reconciliations")
  List<ReconciliationRunView> runs(@RequestParam(defaultValue = "100") int limit) {
    return reconciliations.list(checkedLimit(limit));
  }

  @GetMapping("/reconciliations/{id}")
  ResponseEntity<ReconciliationRunView> run(@PathVariable String id) {
    ReconciliationRunView reconciliationRun = reconciliations.find(id);
    if (reconciliationRun == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation run was not found");
    }
    return ResponseEntity.ok(reconciliationRun);
  }
}
