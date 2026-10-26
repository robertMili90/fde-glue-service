package com.example.fde.erp.demo;

import com.example.fde.erp.invoice.InvoiceProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@ApiResponse(
    responseCode = "400",
    description = "Invalid request",
    content =
        @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = InvoiceProblem.class)))
@RestController
class DemoController {
  private final DemoControls controls;

  DemoController(DemoControls controls) {
    this.controls = controls;
  }

  @Operation(
      summary = "Configure a persisted local simulator fault",
      responses = {
        @ApiResponse(responseCode = "204", description = "Fault configured", content = @Content)
      })
  @PostMapping("/demo/faults")
  ResponseEntity<Void> fault(@Valid @RequestBody FaultRequest request) {
    controls.configure(request);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Inspect invoice and effect counts",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Current state",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = DemoState.class)))
      })
  @GetMapping("/demo/state")
  DemoState state() {
    return controls.state();
  }

  @Operation(
      summary = "Clear this simulator data and faults",
      responses = {
        @ApiResponse(
            responseCode = "204",
            description = "Local simulator reset",
            content = @Content)
      })
  @PostMapping("/demo/reset")
  ResponseEntity<Void> reset() {
    controls.reset();
    return ResponseEntity.noContent().build();
  }
}
