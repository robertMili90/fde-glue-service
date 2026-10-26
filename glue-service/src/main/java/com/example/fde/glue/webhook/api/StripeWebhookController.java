package com.example.fde.glue.webhook.api;

import com.example.fde.glue.http.ApiProblem;
import com.example.fde.glue.observability.FailureDiagnostics;
import com.example.fde.glue.stripe.InvalidStripeWebhook;
import com.example.fde.glue.stripe.WebhookRejection;
import com.example.fde.glue.webhook.application.AcceptStripeWebhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {
  public record Receipt(AcceptStripeWebhook.Status status) {}

  private static final String PERSISTENCE_UNAVAILABLE = "PERSISTENCE_UNAVAILABLE";
  private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
  private final AcceptStripeWebhook acceptance;

  public StripeWebhookController(AcceptStripeWebhook acceptance) {
    this.acceptance = acceptance;
  }

  @Operation(
      summary = "Verify and durably accept a Stripe test event",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = "application/json",
                      schema =
                          @Schema(
                              type = "object",
                              description =
                                  "Stripe snapshot event object; signature is computed over the exact raw body"))),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Recorded, duplicate or safely ignored event",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Receipt.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid signature, event or test-mode reference",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
            responseCode = "503",
            description = "Verification unavailable or durable acceptance failed; retry delivery",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiProblem.class)))
      })
  @PostMapping("/webhooks/stripe")
  public Receipt receive(
      @RequestBody String rawBody, @RequestHeader("Stripe-Signature") String signature) {
    return new Receipt(acceptance.accept(rawBody, signature));
  }

  @ExceptionHandler(InvalidStripeWebhook.class)
  public ResponseEntity<ApiProblem> invalidWebhook(InvalidStripeWebhook invalid) {
    int status = invalid.reason() == WebhookRejection.WEBHOOK_NOT_CONFIGURED ? 503 : 400;
    return ResponseEntity.status(status)
        .body(new ApiProblem(invalid.reason().name(), rejectionMessage(invalid.reason())));
  }

  private String rejectionMessage(WebhookRejection reason) {
    return switch (reason) {
      case WEBHOOK_NOT_CONFIGURED -> "Stripe webhook verification is not configured";
      case INVALID_WEBHOOK -> "Webhook signature or JSON body is invalid";
      case LIVE_OR_UNKNOWN_MODE_EVENT -> "Only explicitly test-mode events are accepted";
      case INVALID_EVENT_REFERENCE -> "Webhook event or payment reference is invalid";
      case LIVE_RESOURCE_IN_EVENT -> "Live-mode resources are not accepted";
    };
  }

  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  public ResponseEntity<ApiProblem> persistenceUnavailable(RuntimeException failure) {
    // A stable failure category preserves diagnostics without logging payloads,
    // credentials, or SQL exceptions that may embed request values.
    log.error(
        "webhook persistence failed; work not acknowledged; diagnostic={}",
        FailureDiagnostics.describe(failure));
    return ResponseEntity.status(503)
        .body(
            new ApiProblem(
                PERSISTENCE_UNAVAILABLE, "Webhook could not be persisted; retry delivery"));
  }
}
