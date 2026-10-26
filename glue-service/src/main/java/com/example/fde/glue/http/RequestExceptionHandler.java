package com.example.fde.glue.http;

import com.example.fde.glue.http.ApiProblem.FieldViolation;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = "com.example.fde.glue")
public class RequestExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiProblem> validation(MethodArgumentNotValidException invalid) {
    List<FieldViolation> errors =
        invalid.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
            .distinct()
            .sorted(
                Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message))
            .toList();
    return badRequest("Request validation failed", errors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiProblem> unreadable(HttpMessageNotReadableException invalid) {
    String field = "body";
    if (invalid.getCause() instanceof JsonMappingException mapping) {
      String path =
          mapping.getPath().stream()
              .map(
                  reference ->
                      reference.getFieldName() == null
                          ? "[" + reference.getIndex() + "]"
                          : reference.getFieldName())
              .collect(Collectors.joining("."));
      if (!path.isEmpty()) {
        field = path;
      }
    }
    // Parser messages can contain the rejected value or the whole raw body.
    return badRequest(
        "Malformed request body",
        List.of(
            new FieldViolation(field, "must contain valid JSON with the documented field types")));
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ApiProblem> missingHeader(MissingRequestHeaderException invalid) {
    return badRequest(
        "Required header is missing",
        List.of(new FieldViolation(invalid.getHeaderName(), "required header is missing")));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<ApiProblem> missingParameter(MissingServletRequestParameterException invalid) {
    return badRequest(
        "Required query parameter is missing",
        List.of(
            new FieldViolation(invalid.getParameterName(), "required query parameter is missing")));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiProblem> invalidParameter(MethodArgumentTypeMismatchException invalid) {
    return badRequest(
        "Invalid query or path parameter",
        List.of(new FieldViolation(invalid.getName(), "must have the documented type")));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ApiProblem> status(ResponseStatusException failure) {
    String reasonCode = failure.getStatusCode().value() == 404 ? "NOT_FOUND" : "INVALID_REQUEST";
    return ResponseEntity.status(failure.getStatusCode())
        .body(new ApiProblem(reasonCode, failure.getReason()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiProblem> invalid(IllegalArgumentException invalid) {
    return ResponseEntity.badRequest()
        .body(new ApiProblem("INVALID_REQUEST", invalid.getMessage()));
  }

  private ResponseEntity<ApiProblem> badRequest(String message, List<FieldViolation> errors) {
    return ResponseEntity.badRequest().body(new ApiProblem("INVALID_REQUEST", message, errors));
  }
}
