package com.example.fde.glue.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ApiProblem(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reasonCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldViolation> errors) {
  public record FieldViolation(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {}

  public ApiProblem(String reasonCode, String message) {
    this(reasonCode, message, List.of());
  }
}
