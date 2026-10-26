package com.example.fde.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fde.erp.demo.DemoState;
import com.example.fde.erp.invoice.FinancialStatus;
import com.example.fde.erp.invoice.InvoicePage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:erp_api;DB_CLOSE_DELAY=-1",
      "logging.level.root=WARN"
    })
class ErpApiTest {
  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper json;
  @LocalServerPort int port;
  static final String PAID =
      """
            {"paymentIntentId":"pi_demo","currency":"USD","amountCollectedMinor":1200,"amountRefundedMinor":0,"status":"PAID"}
            """;

  @BeforeEach
  void reset() {
    http.postForEntity("/demo/reset", null, Void.class);
    assertThat(
            http.postForEntity(
                    "/invoices",
                    Map.of(
                        "id",
                        "inv_demo",
                        "expectedAmountMinor",
                        1200,
                        "currency",
                        "USD",
                        "paymentIntentId",
                        "pi_demo"),
                    String.class)
                .getStatusCode()
                .value())
        .isEqualTo(201);
  }

  ResponseEntity<String> put(String key, String version, String body) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Idempotency-Key", key);
    headers.set("If-Match", version);
    return http.exchange(
        "/invoices/inv_demo/payment-projection",
        HttpMethod.PUT,
        new HttpEntity<>(body, headers),
        String.class);
  }

  void fault(String mode, int count, int delay) {
    assertThat(
            http.postForEntity(
                    "/demo/faults",
                    Map.of("mode", mode, "remainingRequests", count, "delayMs", delay),
                    Void.class)
                .getStatusCode()
                .value())
        .isEqualTo(204);
  }

  @Test
  void generatedContractDescribesActualSuccessAndFailureResponses() throws Exception {
    JsonNode document = json.readTree(http.getForObject("/v3/api-docs", String.class));
    JsonNode paths = document.path("paths");
    JsonNode createResponses = paths.path("/invoices").path("post").path("responses");
    assertThat(createResponses.has("201")).isTrue();
    assertThat(createResponses.has("200")).isFalse();
    assertThat(createResponses.has("400")).isTrue();
    assertThat(createResponses.has("409")).isTrue();
    String invoiceSchema =
        createResponses
            .path("201")
            .path("content")
            .path("application/json")
            .path("schema")
            .path("$ref")
            .asText();
    assertThat(invoiceSchema).endsWith("/InvoiceView");
    JsonNode projectionResponses =
        paths.path("/invoices/{invoiceId}/payment-projection").path("put").path("responses");
    for (String status : new String[] {"200", "400", "404", "409", "412", "422", "503"}) {
      assertThat(projectionResponses.has(status)).as("projection status %s", status).isTrue();
    }
    for (String path : new String[] {"/demo/faults", "/demo/reset"}) {
      assertThat(paths.path(path).path("post").path("responses").has("204")).isTrue();
      assertThat(paths.path(path).path("post").path("responses").has("200")).isFalse();
    }
    assertThat(http.getForEntity("/invoices/missing", String.class).getStatusCode().value())
        .isEqualTo(404);
    fault("NONE", 1, 0);
    assertThat(put("stale", "\"0\"", PAID).getStatusCode().value()).isEqualTo(412);
    assertThat(put("invalid", "\"1\"", PAID.replace("USD", "EUR")).getStatusCode().value())
        .isEqualTo(422);
  }

  @Test
  void malformedValidationAndHeaderErrorsHaveSafeFieldSpecificProblems() throws Exception {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    assertProblem(
        http.postForEntity("/invoices", new HttpEntity<>("{", headers), String.class), "body");
    String invalidMoney =
        "{\"id\":\"invalid\",\"expectedAmountMinor\":\"private-value\",\"currency\":\"USD\",\"paymentIntentId\":\"pi_invalid\"}";
    var moneyFailure =
        http.postForEntity("/invoices", new HttpEntity<>(invalidMoney, headers), String.class);
    assertProblem(moneyFailure, "expectedAmountMinor");
    assertThat(moneyFailure.getBody()).doesNotContain("private-value");
    assertProblem(
        http.postForEntity(
            "/invoices",
            Map.of(
                "id",
                "invalid",
                "expectedAmountMinor",
                0,
                "currency",
                "USD",
                "paymentIntentId",
                "pi_invalid"),
            String.class),
        "expectedAmountMinor");
    assertProblem(
        http.postForEntity("/demo/faults", Map.of("mode", "NONE", "delayMs", -1), String.class),
        "delayMs");
    assertProblem(http.getForEntity("/invoices?limit=private-value", String.class), "limit");
    for (String missingHeader : new String[] {"If-Match", "Idempotency-Key"}) {
      var projectionHeaders = new HttpHeaders();
      projectionHeaders.setContentType(MediaType.APPLICATION_JSON);
      projectionHeaders.set("If-Match", "\"1\"");
      projectionHeaders.set("Idempotency-Key", "header_test");
      projectionHeaders.remove(missingHeader);
      assertProblem(
          http.exchange(
              "/invoices/inv_demo/payment-projection",
              HttpMethod.PUT,
              new HttpEntity<>(PAID, projectionHeaders),
              String.class),
          missingHeader);
    }
    JsonNode queryFailure =
        json.readTree(http.getForEntity("/invoices?limit=0", String.class).getBody());
    assertThat(queryFailure.path("reasonCode").asText()).isEqualTo("INVALID_REQUEST");
    assertThat(queryFailure.path("message").asText()).isEqualTo("limit must be 1..100");
    assertThat(http.getForObject("/demo/state", DemoState.class)).isEqualTo(new DemoState(1, 0, 0));
  }

  private void assertProblem(ResponseEntity<String> response, String field) throws Exception {
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    JsonNode problem = json.readTree(response.getBody());
    assertThat(problem.path("reasonCode").asText()).isEqualTo("INVALID_REQUEST");
    assertThat(problem.path("message").asText()).isNotEmpty();
    assertThat(problem.path("errors").get(0).path("field").asText()).isEqualTo(field);
    assertThat(problem.has("timestamp")).isFalse();
    assertThat(problem.has("trace")).isFalse();
  }

  @Test
  void replayPrecedesStaleVersionAndFaultsAndChangedPayloadConflicts() {
    var first = put("same", "\"1\"", PAID);
    assertThat(first.getStatusCode().value()).isEqualTo(200);
    fault("FAIL_BEFORE_WRITE", 1, 0);
    assertThat(put("same", "\"1\"", PAID).getBody()).isEqualTo(first.getBody());
    assertThat(put("same", "\"1\"", PAID.replace("1200", "900")).getStatusCode().value())
        .isEqualTo(409);
    assertThat(put("unseen", "\"2\"", PAID).getStatusCode().value()).isEqualTo(503);
    assertThat(put("unseen", "\"2\"", PAID).getStatusCode().value()).isEqualTo(200);
    assertThat(http.getForObject("/demo/state", DemoState.class)).isEqualTo(new DemoState(1, 1, 1));
  }

  @Test
  void rejectsStaleVersionAndIncompatibleFinancialWrites() {
    fault("VERSION_CONFLICT", 1, 0);
    assertThat(put("k", "\"1\"", PAID).getStatusCode().value()).isEqualTo(412);
    assertThat(put("k", "\"1\"", PAID).getStatusCode().value()).isEqualTo(412);
    assertThat(put("k", "\"2\"", PAID.replace("USD", "EUR")).getStatusCode().value())
        .isEqualTo(422);
    assertThat(put("k", "\"2\"", PAID).getStatusCode().value()).isEqualTo(200);
    String refunded =
        PAID.replace("\"amountRefundedMinor\":0", "\"amountRefundedMinor\":1200")
            .replace("\"PAID\"", "\"REFUNDED\"");
    assertThat(put("refund", "\"3\"", refunded).getStatusCode().value()).isEqualTo(200);
    assertThat(put("regression", "\"4\"", PAID).getStatusCode().value()).isEqualTo(422);
    assertThat(http.getForObject("/demo/state", DemoState.class).appliedUpdateCount()).isEqualTo(2);
  }

  @Test
  void commitsBeforeDelayedResponseAndReplaysOneProjection() throws Exception {
    fault("APPLY_THEN_DELAY", 1, 1000);
    try (var client = HttpClient.newHttpClient()) {
      var request =
          HttpRequest.newBuilder(
                  URI.create("http://localhost:" + port + "/invoices/inv_demo/payment-projection"))
              .header("Content-Type", "application/json")
              .header("Idempotency-Key", "timeout")
              .header("If-Match", "\"1\"")
              .timeout(Duration.ofMillis(300))
              .PUT(HttpRequest.BodyPublishers.ofString(PAID))
              .build();
      assertThatThrownBy(() -> client.send(request, HttpResponse.BodyHandlers.ofString()))
          .isInstanceOf(HttpTimeoutException.class);
      assertThat(http.getForObject("/invoices/inv_demo", String.class)).contains("PAID");
      assertThat(put("timeout", "\"1\"", PAID).getStatusCode().value()).isEqualTo(200);
      assertThat(http.getForObject("/demo/state", DemoState.class))
          .isEqualTo(new DemoState(1, 1, 1));
    }
  }

  @Test
  void validatesIntegralAmountsAndUniqueAssociationsAndPagesEveryStatus() {
    assertThat(
            http.postForEntity(
                    "/invoices",
                    Map.of(
                        "id",
                        "other",
                        "expectedAmountMinor",
                        1200,
                        "currency",
                        "USD",
                        "paymentIntentId",
                        "pi_demo"),
                    String.class)
                .getStatusCode()
                .value())
        .isEqualTo(409);
    assertThat(
            http.postForEntity(
                    "/invoices",
                    Map.of(
                        "id",
                        "bad/path",
                        "expectedAmountMinor",
                        500,
                        "currency",
                        "USD",
                        "paymentIntentId",
                        "pi_bad_path"),
                    String.class)
                .getStatusCode()
                .value())
        .isEqualTo(400);
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    assertThat(
            http.postForEntity(
                    "/invoices",
                    new HttpEntity<>(
                        """
                {"id":"fractional","expectedAmountMinor":1.5,"currency":"USD","paymentIntentId":"pi_fraction"}
                """,
                        headers),
                    String.class)
                .getStatusCode()
                .value())
        .isEqualTo(400);
    assertThat(
            http.postForEntity(
                    "/invoices",
                    Map.of(
                        "id",
                        "z_last",
                        "expectedAmountMinor",
                        500,
                        "currency",
                        "USD",
                        "paymentIntentId",
                        "pi_last"),
                    String.class)
                .getStatusCode()
                .value())
        .isEqualTo(201);
    put("paid", "\"1\"", PAID);
    var page = http.getForObject("/invoices?limit=1", InvoicePage.class);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().getFirst().financialStatus()).isEqualTo(FinancialStatus.PAID);
    assertThat(
            http.getForObject("/invoices?limit=1&cursor=" + page.nextCursor(), InvoicePage.class)
                .items()
                .getFirst()
                .id())
        .isEqualTo("z_last");
  }
}
