package com.example.fde.erp.invoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record InvoiceProblem(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reasonCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldViolation> errors)
    implements InvoiceResponse {
  public record FieldViolation(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {}

  public InvoiceProblem(String reasonCode, String message) {
    this(reasonCode, message, List.of());
  }
}
