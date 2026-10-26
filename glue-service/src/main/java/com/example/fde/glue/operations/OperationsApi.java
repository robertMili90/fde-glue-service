package com.example.fde.glue.operations;

import com.example.fde.glue.http.ApiProblem;
import com.example.fde.glue.reconciliation.ReconciliationRunView;
import com.example.fde.glue.reconciliation.ReconciliationStore;
import com.example.fde.glue.work.AttemptView;
import com.example.fde.glue.work.WorkStore;
import com.example.fde.glue.work.WorkView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

@ApiResponse(
    responseCode = "400",
    description = "Invalid request",
    content =
        @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ApiProblem.class)))
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

  @Operation(
      summary = "List durable work",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Current state",
            content =
                @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = WorkView.class))))
      })
  @GetMapping("/work")
  List<WorkView> list(
      @RequestParam(defaultValue = "") String after,
      @RequestParam(defaultValue = "100") int limit) {
    return workStore.list(after, checkedLimit(limit));
  }

  @Operation(
      summary = "Inspect one work item",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Current state",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = WorkView.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Resource was not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiProblem.class)))
      })
  @GetMapping("/work/{paymentIntentId}")
  ResponseEntity<WorkView> get(@PathVariable String paymentIntentId) {
    WorkView work = workStore.find(paymentIntentId);
    if (work == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item was not found");
    }
    return ResponseEntity.ok(work);
  }

  @Operation(
      summary = "Inspect latest attempts",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Current state",
            content =
                @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = AttemptView.class))))
      })
  @GetMapping("/work/{paymentIntentId}/attempts")
  List<AttemptView> attempts(
      @PathVariable String paymentIntentId, @RequestParam(defaultValue = "100") int limit) {
    return workStore.attempts(paymentIntentId, checkedLimit(limit));
  }

  @Operation(
      summary = "Release a held job and reset its retry budget",
      responses = {
        @ApiResponse(
            responseCode = "202",
            description = "Work accepted for asynchronous processing",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = WorkView.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Resource was not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiProblem.class)))
      })
  @PostMapping("/work/{paymentIntentId}/requeue")
  ResponseEntity<WorkView> requeue(@PathVariable String paymentIntentId) {
    if (!workStore.requeue(paymentIntentId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item was not found");
    }
    return ResponseEntity.accepted().body(workStore.find(paymentIntentId));
  }

  @Operation(
      summary = "Queue or resume linked-invoice enumeration",
      responses = {
        @ApiResponse(
            responseCode = "202",
            description = "Enumeration accepted; payments synchronize asynchronously",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ReconciliationRunView.class)))
      })
  @PostMapping("/reconciliations")
  ResponseEntity<ReconciliationRunView> reconcile() {
    return ResponseEntity.accepted().body(reconciliations.request());
  }

  @Operation(
      summary = "List reconciliation runs",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Current state",
            content =
                @Content(
                    mediaType = "application/json",
                    array =
                        @ArraySchema(
                            schema = @Schema(implementation = ReconciliationRunView.class))))
      })
  @GetMapping("/reconciliations")
  List<ReconciliationRunView> runs(@RequestParam(defaultValue = "100") int limit) {
    return reconciliations.list(checkedLimit(limit));
  }

  @Operation(
      summary = "Inspect a reconciliation run",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Current state",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ReconciliationRunView.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Resource was not found",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiProblem.class)))
      })
  @GetMapping("/reconciliations/{id}")
  ResponseEntity<ReconciliationRunView> run(@PathVariable String id) {
    ReconciliationRunView reconciliationRun = reconciliations.find(id);
    if (reconciliationRun == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation run was not found");
    }
    return ResponseEntity.ok(reconciliationRun);
  }
}
