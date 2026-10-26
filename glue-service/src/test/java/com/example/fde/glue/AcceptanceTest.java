package com.example.fde.glue;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fde.erp.ErpSimulatorApplication;
import com.example.fde.glue.reconciliation.Reconciler;
import com.example.fde.glue.reconciliation.ReconciliationState;
import com.example.fde.glue.reconciliation.ReconciliationStore;
import com.example.fde.glue.work.AttemptOutcome;
import com.example.fde.glue.work.AttemptView;
import com.example.fde.glue.work.SyncWorker;
import com.example.fde.glue.work.WorkState;
import com.example.fde.glue.work.WorkStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.net.Webhook;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcceptanceTest {
  static final String SECRET = "whsec_offline_fixture";
  static final MutableClock TIME = new MutableClock();
  final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  final Map<String, Payment> payments = new ConcurrentHashMap<>();
  HttpServer stripe;
  ExecutorService stripeThreads;
  HttpClient http;
  ServletWebServerApplicationContext erp;
  ServletWebServerApplicationContext glue;
  Path directory;
  volatile CountDownLatch entered;
  volatile CountDownLatch release;
  volatile int stripeFailure;
  volatile int failErpPage; // exercised through an ERP HTTP proxy below
  final Map<String, String> scriptedErpPages = new ConcurrentHashMap<>();
  volatile String stalledErpPath;
  volatile CountDownLatch erpBodyEntered;
  volatile CountDownLatch releaseErpBody;
  HttpServer erpProxy;
  ExecutorService proxyThreads;
  final AtomicInteger providerReads = new AtomicInteger();
  final AtomicInteger stripeRequests = new AtomicInteger();

  record Refund(String id, long amount, String status, String currency) {}

  record Payment(long amount, String currency, String status, boolean live, List<Refund> refunds) {
    static Payment paid() {
      return new Payment(1200, "usd", "succeeded", false, List.of());
    }

    Payment refunds(Refund... values) {
      return new Payment(amount, currency, status, live, List.of(values));
    }
  }

  @BeforeAll
  void start() throws Exception {
    directory = Files.createTempDirectory("fde-acceptance-");
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    stripe = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    stripeThreads = Executors.newCachedThreadPool();
    stripe.setExecutor(stripeThreads);
    stripe.createContext(
        "/",
        exchange -> {
          try {
            stripeRequests.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            String body;
            int status = stripeFailure;
            if (status != 0) {
              exchange.getResponseHeaders().set("Retry-After", "2");
              body = "{\"error\":{\"message\":\"offline failure\",\"type\":\"api_error\"}}";
            } else if (path.startsWith("/v1/payment_intents/")) {
              providerReads.incrementAndGet();
              String id = path.substring(path.lastIndexOf('/') + 1);
              Payment payment = payments.get(id);
              if (payment == null) {
                status = 404;
                body = "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"missing\"}}";
              } else {
                body =
                    json.writeValueAsString(
                        Map.of(
                            "id",
                            id,
                            "object",
                            "payment_intent",
                            "amount",
                            payment.amount,
                            "amount_received",
                            payment.status.equals("succeeded") ? payment.amount : 0,
                            "currency",
                            payment.currency,
                            "status",
                            payment.status,
                            "livemode",
                            payment.live,
                            "capture_method",
                            "automatic"));
                var barrier = entered;
                var waitFor = release;
                if (barrier != null) {
                  entered = null;
                  barrier.countDown();
                  waitFor.await(5, TimeUnit.SECONDS);
                }
              }
            } else if (path.equals("/v1/refunds")) {
              var query = query(exchange.getRequestURI().getRawQuery());
              assertThat(query.get("limit")).isEqualTo("100");
              String id = query.get("payment_intent");
              var refunds = payments.get(id).refunds;
              int index = 0;
              if (query.containsKey("starting_after")) {
                for (int i = 0; i < refunds.size(); i++) {
                  if (refunds.get(i).id.equals(query.get("starting_after"))) {
                    index = i + 1;
                  }
                }
              }
              List<Object> data = new ArrayList<>();
              if (index < refunds.size()) {
                var refund = refunds.get(index);
                data.add(
                    Map.of(
                        "id",
                        refund.id,
                        "object",
                        "refund",
                        "payment_intent",
                        id,
                        "amount",
                        refund.amount,
                        "currency",
                        refund.currency,
                        "status",
                        refund.status));
              }
              body =
                  json.writeValueAsString(
                      Map.of(
                          "object",
                          "list",
                          "data",
                          data,
                          "has_more",
                          index + 1 < refunds.size(),
                          "url",
                          "/v1/refunds"));
            } else {
              status = 404;
              body = "{}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status == 0 ? 200 : status, bytes.length);
            exchange.getResponseBody().write(bytes);
          } catch (Exception failure) {
            throw new RuntimeException(failure);
          } finally {
            exchange.close();
          }
        });
    stripe.start();
    erp =
        (ServletWebServerApplicationContext)
            new SpringApplicationBuilder(ErpSimulatorApplication.class)
                .run(
                    "--server.port=0",
                    "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("erp"),
                    "--spring.flyway.locations=classpath:db/erp",
                    "--logging.level.root=WARN",
                    "--spring.main.banner-mode=off");
    // A forwarding HTTP proxy can fail an enumeration page without changing the real ERP.
    erpProxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    proxyThreads = Executors.newCachedThreadPool();
    erpProxy.setExecutor(proxyThreads);
    erpProxy.createContext(
        "/",
        exchange -> {
          try {
            if (exchange.getRequestURI().getPath().equals("/invoices")) {
              String cursor =
                  query(exchange.getRequestURI().getRawQuery()).getOrDefault("cursor", "");
              String scriptedPage = scriptedErpPages.get(cursor);
              if (scriptedPage != null) {
                byte[] bytes = scriptedPage.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                return;
              }
            }
            boolean fail =
                failErpPage != 0
                    && exchange.getRequestURI().getPath().equals("/invoices")
                    && query(exchange.getRequestURI().getRawQuery()).containsKey("cursor");
            if (fail) {
              exchange.sendResponseHeaders(503, -1);
              return;
            }
            var request =
                HttpRequest.newBuilder(URI.create(erpUrl() + exchange.getRequestURI().toString()))
                    .timeout(Duration.ofSeconds(4))
                    .method(
                        exchange.getRequestMethod(),
                        HttpRequest.BodyPublishers.ofByteArray(
                            exchange.getRequestBody().readAllBytes()));
            for (String header : List.of("Content-Type", "Idempotency-Key", "If-Match")) {
              String value = exchange.getRequestHeaders().getFirst(header);
              if (value != null) {
                request.header(header, value);
              }
            }
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(
                response.statusCode(), response.body().length == 0 ? -1 : response.body().length);
            if (exchange.getRequestURI().getPath().equals(stalledErpPath)
                && response.body().length > 0) {
              CountDownLatch allowBody = releaseErpBody;
              exchange.getResponseBody().write(response.body(), 0, 1);
              exchange.getResponseBody().flush();
              erpBodyEntered.countDown();
              allowBody.await(8, TimeUnit.SECONDS);
              exchange.getResponseBody().write(response.body(), 1, response.body().length - 1);
            } else if (response.body().length > 0) {
              exchange.getResponseBody().write(response.body());
            }
          } catch (Exception ignored) {
            /* expected when the glue caller deliberately times out */
          } finally {
            exchange.close();
          }
        });
    erpProxy.start();
    startGlue();
  }

  void startGlue() {
    startGlue(10000);
  }

  void startGlue(int maxScanPages) {
    glue =
        (ServletWebServerApplicationContext)
            new SpringApplicationBuilder(GlueServiceApplication.class, TestClockConfiguration.class)
                .run(
                    "--server.port=0",
                    "--spring.datasource.url=jdbc:h2:file:"
                        + directory.resolve("glue")
                        + ";DB_CLOSE_ON_EXIT=FALSE",
                    "--spring.flyway.locations=classpath:db/glue",
                    "--sync.enabled=false",
                    "--sync.http-timeout-ms=500",
                    "--sync.reconciliation-max-pages=" + maxScanPages,
                    "--sync.reconciliation-page-size=1",
                    "--sync.reconciliation-batch-pages=1",
                    "--stripe.secret-key=sk_test_offline",
                    "--stripe.webhook-secret=" + SECRET,
                    "--stripe.api-base=http://127.0.0.1:" + stripe.getAddress().getPort(),
                    "--erp.base-url=http://127.0.0.1:" + erpProxy.getAddress().getPort(),
                    "--logging.level.root=WARN",
                    "--spring.main.banner-mode=off");
  }

  @AfterAll
  void stop() throws Exception {
    if (release != null) {
      release.countDown();
    }
    if (releaseErpBody != null) {
      releaseErpBody.countDown();
    }
    if (glue != null) {
      glue.close();
    }
    if (erp != null) {
      erp.close();
    }
    if (stripe != null) {
      stripe.stop(0);
    }
    if (erpProxy != null) {
      erpProxy.stop(0);
    }
    if (stripeThreads != null) {
      stripeThreads.shutdownNow();
    }
    if (proxyThreads != null) {
      proxyThreads.shutdownNow();
    }
    if (http != null) {
      http.close();
    }
    if (directory != null) {
      try (var paths = Files.walk(directory)) {
        for (var p : paths.sorted(Comparator.reverseOrder()).toList()) {
          Files.deleteIfExists(p);
        }
      }
    }
  }

  @BeforeEach
  void reset() throws Exception {
    TIME.now = Instant.parse("2026-10-23T00:00:00Z");
    payments.clear();
    scriptedErpPages.clear();
    stripeFailure = 0;
    failErpPage = 0;
    stalledErpPath = null;
    entered = null;
    release = null;
    providerReads.set(0);
    stripeRequests.set(0);
    request(erpUrl(), "POST", "/demo/reset", null);
    var jdbc = glue.getBean(JdbcTemplate.class);
    jdbc.update("DELETE FROM glue.attempt");
    jdbc.update("DELETE FROM glue.received_event");
    jdbc.update("DELETE FROM glue.work_item");
    jdbc.update("DELETE FROM glue.reconciliation_run");
    jdbc.update("UPDATE glue.work_gate SET current_scan_id = NULL");
  }

  String erpUrl() {
    return "http://localhost:" + erp.getWebServer().getPort();
  }

  String glueUrl() {
    return "http://localhost:" + glue.getWebServer().getPort();
  }

  HttpResponse<String> request(String base, String method, String path, Object body)
      throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  JsonNode get(String base, String path) throws Exception {
    return json.readTree(request(base, "GET", path, null).body());
  }

  void fixture(String id, String payment) throws Exception {
    payments.put(payment, Payment.paid());
    assertThat(
            request(
                    erpUrl(),
                    "POST",
                    "/invoices",
                    Map.of(
                        "id",
                        id,
                        "expectedAmountMinor",
                        1200,
                        "currency",
                        "USD",
                        "paymentIntentId",
                        payment))
                .statusCode())
        .isEqualTo(201);
  }

  HttpResponse<String> event(String id, String payment, String type, boolean live, boolean valid)
      throws Exception {
    var object =
        type.startsWith("refund.")
            ? Map.of("id", "re_notice", "object", "refund", "payment_intent", payment)
            : Map.of("id", payment, "object", "payment_intent");
    String body =
        json.writeValueAsString(
            Map.of(
                "id",
                id,
                "type",
                type,
                "object",
                "event",
                "livemode",
                live,
                "created",
                TIME.instant().getEpochSecond() - (id.contains("old") ? 86400 : 0),
                "data",
                Map.of("object", object)));
    String signature =
        Webhook.Signature.generateSignatureHeader(
            body, valid ? SECRET : "wrong_secret", TIME.instant().getEpochSecond());
    return http.send(
        HttpRequest.newBuilder(URI.create(glueUrl() + "/webhooks/stripe"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .header("Stripe-Signature", signature)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  void event(String id, String payment) throws Exception {
    assertThat(event(id, payment, "payment_intent.succeeded", false, true).statusCode())
        .isEqualTo(200);
  }

  WorkStore store() {
    return glue.getBean(WorkStore.class);
  }

  SyncWorker worker() {
    return glue.getBean(SyncWorker.class);
  }

  void state(String payment, String state) {
    assertThat(store().find(payment).state()).isEqualTo(WorkState.valueOf(state));
  }

  void due(String payment) {
    TIME.now = store().find(payment).nextEligibleAt();
  }

  void fault(String mode, int count) throws Exception {
    assertThat(
            request(
                    erpUrl(),
                    "POST",
                    "/demo/faults",
                    Map.of("mode", mode, "remainingRequests", count, "delayMs", 1500))
                .statusCode())
        .isEqualTo(204);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/invoices/by-payment/pi_body_timeout",
        "/invoices/inv_body_timeout/payment-projection"
      })
  void stalledErpBodySchedulesRetryAndReleasesWorkerWithoutDuplicatingEffects(String stalledPath)
      throws Exception {
    fixture("inv_body_timeout", "pi_body_timeout");
    fixture("inv_next", "pi_next");
    event("evt_body_timeout", "pi_body_timeout");
    event("evt_next", "pi_next");
    stalledErpPath = stalledPath;
    erpBodyEntered = new CountDownLatch(1);
    releaseErpBody = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> processing = executor.submit(() -> worker().tick());
      assertThat(erpBodyEntered.await(3, TimeUnit.SECONDS)).isTrue();
      processing.get(3, TimeUnit.SECONDS);
      state("pi_body_timeout", "RETRY_SCHEDULED");
      assertThat(store().find("pi_body_timeout").reasonCode()).isEqualTo("ERP_CONNECTION");
      assertThat(releaseErpBody.getCount()).isEqualTo(1);
      worker().tick();
      state("pi_next", "DONE");
      stalledErpPath = null;
      due("pi_body_timeout");
      worker().tick();
      state("pi_body_timeout", "DONE");
      JsonNode counts = get(erpUrl(), "/demo/state");
      assertThat(counts.path("paymentProjectionCount").asInt()).isEqualTo(2);
      assertThat(counts.path("appliedUpdateCount").asInt()).isEqualTo(2);
    } finally {
      releaseErpBody.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void stalledEnumerationBodyFailsVisiblyAndCanResume() throws Exception {
    fixture("inv_body_timeout", "pi_body_timeout");
    ReconciliationStore scans = glue.getBean(ReconciliationStore.class);
    String runId = scans.request().id();
    stalledErpPath = "/invoices";
    erpBodyEntered = new CountDownLatch(1);
    releaseErpBody = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> processing = executor.submit(() -> glue.getBean(Reconciler.class).tick());
      assertThat(erpBodyEntered.await(3, TimeUnit.SECONDS)).isTrue();
      processing.get(3, TimeUnit.SECONDS);
      assertThat(scans.find(runId).state()).isEqualTo(ReconciliationState.FAILED);
      assertThat(scans.find(runId).lastFailure()).isEqualTo("ERP_CONNECTION");
      assertThat(scans.find(runId).pagesProcessed()).isZero();
      stalledErpPath = null;
      scans.request();
      glue.getBean(Reconciler.class).tick();
      assertThat(scans.find(runId).state()).isEqualTo(ReconciliationState.COMPLETE);
      worker().tick();
      state("pi_body_timeout", "DONE");
    } finally {
      releaseErpBody.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void generatedOperationsContractMatchesQueuedResponsesAndWebhookObjectBody() throws Exception {
    JsonNode document = get(glueUrl(), "/v3/api-docs");
    JsonNode paths = document.path("paths");
    assertThat(paths.path("/operations/reconciliations").path("post").path("responses").has("202"))
        .isTrue();
    assertThat(paths.path("/operations/reconciliations").path("post").path("responses").has("200"))
        .isFalse();
    JsonNode requeue =
        paths.path("/operations/work/{paymentIntentId}/requeue").path("post").path("responses");
    assertThat(requeue.has("202")).isTrue();
    assertThat(requeue.has("404")).isTrue();
    JsonNode webhook = paths.path("/webhooks/stripe").path("post");
    assertThat(webhook.path("responses").has("400")).isTrue();
    assertThat(webhook.path("responses").has("503")).isTrue();
    assertThat(
            webhook
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("type")
                .asText())
        .isEqualTo("object");
    var queued = request(glueUrl(), "POST", "/operations/reconciliations", null);
    assertThat(queued.statusCode()).isEqualTo(202);
    assertThat(json.readTree(queued.body()).path("state").asText()).isEqualTo("QUEUED");
    fixture("inv_requeue", "pi_requeue");
    event("evt_requeue", "pi_requeue");
    stripeFailure = 401;
    worker().tick();
    assertThat(request(glueUrl(), "POST", "/operations/work/pi_requeue/requeue", null).statusCode())
        .isEqualTo(202);
  }

  @Test
  void operationsErrorsUseSafeConsistentProblems() throws Exception {
    for (String path :
        List.of(
            "/operations/work?limit=not-a-number",
            "/operations/work?limit=0",
            "/operations/work/pi_missing",
            "/operations/work/pi_missing/requeue")) {
      String method = path.endsWith("/requeue") ? "POST" : "GET";
      var response = request(glueUrl(), method, path, null);
      assertThat(response.statusCode()).isIn(400, 404);
      JsonNode problem = json.readTree(response.body());
      assertThat(problem.path("reasonCode").asText()).isNotEmpty();
      assertThat(problem.path("message").asText()).isNotEmpty();
      assertThat(problem.has("timestamp")).isFalse();
      assertThat(response.body()).doesNotContain("not-a-number");
      if (path.contains("not-a-number")) {
        assertThat(problem.path("errors").get(0).path("field").asText()).isEqualTo("limit");
      }
    }
    var missingBody = request(glueUrl(), "POST", "/webhooks/stripe", null);
    assertThat(missingBody.statusCode()).isEqualTo(400);
    assertThat(json.readTree(missingBody.body()).path("errors").get(0).path("field").asText())
        .isEqualTo("body");
    var missingSignature =
        request(glueUrl(), "POST", "/webhooks/stripe", Map.of("livemode", false));
    assertThat(missingSignature.statusCode()).isEqualTo(400);
    JsonNode signatureProblem = json.readTree(missingSignature.body());
    assertThat(signatureProblem.path("reasonCode").asText()).isEqualTo("INVALID_REQUEST");
    assertThat(signatureProblem.path("errors").get(0).path("field").asText())
        .isEqualTo("Stripe-Signature");
  }

  @Test
  void signedConcurrentDuplicatesAndEquivalentEventsHaveOneFinancialEffect() throws Exception {
    fixture("invoice", "pi_paid");
    try (var pool = Executors.newFixedThreadPool(6)) {
      var calls = new ArrayList<Future<Integer>>();
      for (int i = 0; i < 6; i++) {
        calls.add(
            pool.submit(
                () ->
                    event("evt_duplicate", "pi_paid", "payment_intent.succeeded", false, true)
                        .statusCode()));
      }
      for (var result : calls) {
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(200);
      }
    }
    assertThat(
            glue.getBean(JdbcTemplate.class)
                .queryForObject("SELECT COUNT(*) FROM glue.received_event", Long.class))
        .isEqualTo(1);
    assertThat(store().find("pi_paid").requestedGeneration()).isEqualTo(1);
    state("pi_paid", "READY");
    worker().tick();
    state("pi_paid", "DONE");
    String key = store().find("pi_paid").operationKey();
    event("evt_distinct", "pi_paid");
    worker().tick();
    assertThat(store().find("pi_paid").operationKey()).isEqualTo(key);
    assertThat(get(erpUrl(), "/demo/state").path("appliedUpdateCount").asInt()).isEqualTo(1);
    assertThat(get(erpUrl(), "/demo/state").path("paymentProjectionCount").asInt()).isEqualTo(1);
    assertThat(request(glueUrl(), "GET", "/operations/work/pi_paid/attempts", null).statusCode())
        .isEqualTo(200);
    assertThat(get(glueUrl(), "/actuator/metrics/sync.work").path("measurements").isArray())
        .isTrue();
  }

  @Test
  void persistenceFailureRollsBackEventAndReturnsRetryableResponse() throws Exception {
    fixture("invoice", "pi_reject");
    var jdbc = glue.getBean(JdbcTemplate.class);
    jdbc.execute(
        "ALTER TABLE glue.work_item ADD CONSTRAINT reject_probe CHECK(payment_intent_id <> 'pi_reject')");
    try {
      assertThat(
              event("evt_retry", "pi_reject", "payment_intent.succeeded", false, true).statusCode())
          .isEqualTo(503);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM glue.received_event", Long.class))
          .isZero();
    } finally {
      jdbc.execute("ALTER TABLE glue.work_item DROP CONSTRAINT reject_probe");
    }
    event("evt_retry", "pi_reject");
    worker().tick();
    state("pi_reject", "DONE");
  }

  @Test
  void transientFailurePersistsBackoffAndEventuallyConverges() throws Exception {
    fixture("invoice", "pi_retry");
    fault("FAIL_BEFORE_WRITE", 2);
    event("evt_retry", "pi_retry");
    worker().tick();
    state("pi_retry", "RETRY_SCHEDULED");
    Instant firstDue = store().find("pi_retry").nextEligibleAt();
    worker().tick();
    assertThat(store().find("pi_retry").totalAttempts()).isEqualTo(1);
    due("pi_retry");
    worker().tick();
    state("pi_retry", "RETRY_SCHEDULED");
    assertThat(store().find("pi_retry").nextEligibleAt()).isAfter(firstDue);
    due("pi_retry");
    worker().tick();
    state("pi_retry", "DONE");
    assertThat(store().attempts("pi_retry", 100)).hasSize(3);
  }

  @Test
  void timeoutAfterCommitConvergesWithoutASecondProjection() throws Exception {
    fixture("invoice", "pi_timeout");
    fault("APPLY_THEN_DELAY", 1);
    event("evt_timeout", "pi_timeout");
    worker().tick();
    state("pi_timeout", "RETRY_SCHEDULED");
    assertThat(get(erpUrl(), "/invoices/invoice").path("financialStatus").asText())
        .isEqualTo("PAID");
    due("pi_timeout");
    worker().tick();
    state("pi_timeout", "DONE");
    assertThat(get(erpUrl(), "/demo/state").path("appliedUpdateCount").asInt()).isEqualTo(1);
    assertThat(get(erpUrl(), "/demo/state").path("paymentProjectionCount").asInt()).isEqualTo(1);
  }

  @Test
  void restartRecoversRunningWorkAndRetainsNotifications() throws Exception {
    fixture("invoice", "pi_restart");
    event("evt_before", "pi_restart");
    var claim = store().claim();
    assertThat(claim).isNotNull();
    state("pi_restart", "RUNNING");
    event("evt_during", "pi_restart");
    glue.close();
    startGlue();
    state("pi_restart", "READY");
    assertThat(store().attempts("pi_restart", 100).getFirst().outcome())
        .isEqualTo(AttemptOutcome.INTERRUPTED);
    worker().tick();
    state("pi_restart", "DONE");
    assertThat(store().find("pi_restart").completedGeneration()).isEqualTo(2);
    assertThat(get(erpUrl(), "/demo/state").path("appliedUpdateCount").asInt()).isEqualTo(1);
  }

  @Test
  void currentRefundPagesWinOverOldPaymentNotification() throws Exception {
    fixture("invoice", "pi_refund");
    event("evt_initial", "pi_refund");
    worker().tick();
    payments.put(
        "pi_refund",
        Payment.paid()
            .refunds(
                new Refund("re_first", 600, "succeeded", "usd"),
                new Refund("re_second", 600, "succeeded", "usd")));
    event("evt_old_success", "pi_refund");
    worker().tick();
    state("pi_refund", "DONE");
    assertThat(get(erpUrl(), "/invoices/invoice").path("financialStatus").asText())
        .isEqualTo("REFUNDED");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "requires_payment_method",
        "requires_confirmation",
        "requires_action",
        "processing"
      })
  void recoverablePaymentStatesWaitWithoutWritingAndCanConverge(String paymentStatus)
      throws Exception {
    fixture("invoice", "pi_waiting");
    payments.put("pi_waiting", new Payment(1200, "usd", paymentStatus, false, List.of()));
    assertThat(
            event("evt_waiting", "pi_waiting", "payment_intent.payment_failed", false, true)
                .statusCode())
        .isEqualTo(200);
    worker().tick();
    state("pi_waiting", "WAITING_PROVIDER");
    assertThat(store().find("pi_waiting").reasonCode()).isEqualTo("PAYMENT_NOT_SUCCEEDED");
    assertThat(store().find("pi_waiting").nextEligibleAt()).isAfter(TIME.instant());
    assertThat(store().find("pi_waiting").transientFailures()).isZero();
    assertThat(get(erpUrl(), "/invoices/invoice").path("financialStatus").asText())
        .isEqualTo("UNPAID");
    assertThat(get(erpUrl(), "/demo/state").path("appliedUpdateCount").asInt()).isZero();

    due("pi_waiting");
    worker().tick();
    state("pi_waiting", "WAITING_PROVIDER");
    assertThat(store().find("pi_waiting").transientFailures()).isZero();
    payments.put("pi_waiting", Payment.paid());
    due("pi_waiting");
    worker().tick();
    state("pi_waiting", "DONE");
    assertThat(get(erpUrl(), "/invoices/invoice").path("financialStatus").asText())
        .isEqualTo("PAID");
  }

  @Test
  void canceledPaymentStopsPollingAndReconciliationAcrossRestartButRetainsNewEvents()
      throws Exception {
    fixture("invoice", "pi_canceled");
    payments.put("pi_canceled", new Payment(1200, "usd", "canceled", false, List.of()));
    event("evt_canceled", "pi_canceled");
    worker().tick();
    state("pi_canceled", "DONE");
    assertThat(store().find("pi_canceled").reasonCode()).isEqualTo("PAYMENT_CANCELED");
    assertThat(store().find("pi_canceled").nextEligibleAt()).isNull();
    assertThat(store().find("pi_canceled").completedGeneration()).isEqualTo(1);
    assertThat(store().attempts("pi_canceled", 10).getFirst().outcome())
        .isEqualTo(AttemptOutcome.DONE);
    assertThat(json.readTree(store().find("pi_canceled").facts()).path("status").asText())
        .isEqualTo("canceled");

    TIME.now = TIME.now.plus(Duration.ofDays(30));
    worker().tick();
    glue.close();
    startGlue();
    var scans = glue.getBean(ReconciliationStore.class);
    var run = scans.request();
    glue.getBean(Reconciler.class).tick();
    assertThat(scans.find(run.id()).state()).isEqualTo(ReconciliationState.COMPLETE);
    worker().tick();
    state("pi_canceled", "DONE");
    assertThat(store().find("pi_canceled").totalAttempts()).isEqualTo(1);
    assertThat(store().find("pi_canceled").requestedGeneration()).isEqualTo(1);
    assertThat(providerReads.get()).isEqualTo(1);

    event("evt_canceled_late", "pi_canceled");
    state("pi_canceled", "READY");
    worker().tick();
    state("pi_canceled", "DONE");
    assertThat(store().find("pi_canceled").completedGeneration()).isEqualTo(2);
    assertThat(get(erpUrl(), "/invoices/invoice").path("financialStatus").asText())
        .isEqualTo("UNPAID");
    assertThat(get(erpUrl(), "/demo/state").path("appliedUpdateCount").asInt()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"requires_capture", "future_status", ""})
  void unsupportedPaymentStatesAreHeldWithFactsWithoutPollingOrWriting(String paymentStatus)
      throws Exception {
    fixture("invoice", "pi_unsupported");
    payments.put("pi_unsupported", new Payment(1200, "usd", paymentStatus, false, List.of()));
    event("evt_unsupported", "pi_unsupported");
    worker().tick();
    state("pi_unsupported", "NEEDS_REVIEW");
    String expectedReason =
        paymentStatus.equals("requires_capture") ? "UNSUPPORTED_CAPTURE" : "UNKNOWN_PAYMENT_STATUS";
    assertThat(store().find("pi_unsupported").reasonCode()).isEqualTo(expectedReason);
    assertThat(store().find("pi_unsupported").nextEligibleAt()).isNull();
    assertThat(json.readTree(store().find("pi_unsupported").facts()).path("status").asText())
        .isEqualTo(paymentStatus);
    TIME.now = TIME.now.plus(Duration.ofDays(1));
    store().enqueue("pi_unsupported");
    event("evt_unsupported_again", "pi_unsupported");
    worker().tick();
    assertThat(providerReads.get()).isEqualTo(1);
    assertThat(get(erpUrl(), "/invoices/invoice").path("financialStatus").asText())
        .isEqualTo("UNPAID");
    assertThat(get(erpUrl(), "/demo/state").path("appliedUpdateCount").asInt()).isZero();
  }

  @Test
  void conflictsAreHeldAndPendingFailedRefundsDoNotCountAsSuccessful() throws Exception {
    fixture("invoice", "pi_policy");
    payments.put(
        "pi_policy",
        Payment.paid()
            .refunds(
                new Refund("re_pending", 1200, "pending", "usd"),
                new Refund("re_failed", 100, "failed", "usd")));
    event("evt_pending", "pi_policy");
    worker().tick();
    state("pi_policy", "WAITING_PROVIDER");
    assertThat(get(erpUrl(), "/invoices/invoice").path("financialStatus").asText())
        .isEqualTo("PAID");
    payments.put(
        "pi_policy", Payment.paid().refunds(new Refund("re_partial", 100, "succeeded", "usd")));
    due("pi_policy");
    worker().tick();
    state("pi_policy", "NEEDS_REVIEW");
    assertThat(store().find("pi_policy").reasonCode()).isEqualTo("PARTIAL_REFUND");
    store().enqueue("pi_policy");
    event("evt_still_held", "pi_policy");
    worker().tick();
    state("pi_policy", "NEEDS_REVIEW");
    assertThat(get(erpUrl(), "/demo/state").path("appliedUpdateCount").asInt()).isEqualTo(1);
    assertThat(request(glueUrl(), "POST", "/operations/work/pi_policy/requeue", null).statusCode())
        .isEqualTo(202);
    payments.put("pi_policy", Payment.paid());
    worker().tick();
    state("pi_policy", "DONE");
  }

  @Test
  void badSignatureAndLiveEventsOrResourcesProduceNoFinancialEffect() throws Exception {
    fixture("invoice", "pi_live");
    assertThat(
            event("evt_invalid", "pi_live", "payment_intent.succeeded", false, false).statusCode())
        .isEqualTo(400);
    assertThat(event("evt_live", "pi_live", "payment_intent.succeeded", true, true).statusCode())
        .isEqualTo(400);
    assertThat(
            glue.getBean(JdbcTemplate.class)
                .queryForObject("SELECT COUNT(*) FROM glue.received_event", Long.class))
        .isZero();
    payments.put("pi_live", new Payment(1200, "usd", "succeeded", true, List.of()));
    event("evt_resource", "pi_live");
    worker().tick();
    state("pi_live", "NEEDS_REVIEW");
    assertThat(store().find("pi_live").reasonCode()).isEqualTo("LIVE_OR_UNKNOWN_MODE_RESOURCE");
    assertThat(get(erpUrl(), "/demo/state").path("appliedUpdateCount").asInt()).isZero();
  }

  @Test
  void distinctTriggerDuringRemoteCallPreservesAnotherPass() throws Exception {
    fixture("invoice", "pi_wakeup");
    payments.put(
        "pi_wakeup", new Payment(1200, "usd", "requires_payment_method", false, List.of()));
    event("evt_first", "pi_wakeup");
    entered = new CountDownLatch(1);
    release = new CountDownLatch(1);
    CountDownLatch barrier = entered;
    try (var pool = Executors.newSingleThreadExecutor()) {
      var running = pool.submit(() -> worker().tick());
      assertThat(barrier.await(3, TimeUnit.SECONDS)).isTrue();
      payments.put("pi_wakeup", Payment.paid());
      event("evt_new", "pi_wakeup");
      release.countDown();
      running.get(5, TimeUnit.SECONDS);
    } finally {
      release.countDown();
    }
    state("pi_wakeup", "READY");
    worker().tick();
    state("pi_wakeup", "DONE");
    assertThat(store().find("pi_wakeup").completedGeneration()).isEqualTo(2);
  }

  @Test
  void missingFirstWebhookAndLaterRefundOnPaidInvoiceConvergeAcrossBatchesAndRestart()
      throws Exception {
    for (int i = 1; i <= 3; i++) {
      fixture("invoice_" + i, "pi_scan" + i);
    }
    var scans = glue.getBean(ReconciliationStore.class);
    var run = scans.request();
    assertThat(scans.claim().state())
        .isEqualTo(ReconciliationState.RUNNING); // Simulate a lost claim response.
    glue.getBean(Reconciler.class).tick();
    assertThat(scans.find(run.id()).state()).isEqualTo(ReconciliationState.INCOMPLETE);
    assertThat(scans.find(run.id()).cursor()).isEqualTo("invoice_1");
    failErpPage = 1;
    glue.getBean(Reconciler.class).tick();
    assertThat(scans.find(run.id()).state()).isEqualTo(ReconciliationState.FAILED);
    assertThat(scans.find(run.id()).completedAt()).isNull();
    glue.close();
    startGlue();
    scans = glue.getBean(ReconciliationStore.class);
    failErpPage = 0;
    stalledErpPath = null;
    assertThat(scans.request().id()).isEqualTo(run.id());
    glue.getBean(Reconciler.class).tick();
    glue.getBean(Reconciler.class).tick();
    assertThat(scans.find(run.id()).state()).isEqualTo(ReconciliationState.COMPLETE);
    assertThat(scans.find(run.id()).enqueuedReferences()).isEqualTo(3);
    for (int i = 0; i < 3; i++) {
      worker().tick();
    }
    for (int i = 1; i <= 3; i++) {
      state("pi_scan" + i, "DONE");
    }
    assertThat(
            glue.getBean(JdbcTemplate.class)
                .queryForObject("SELECT COUNT(*) FROM glue.received_event", Long.class))
        .isZero();
    TIME.now = TIME.now.plus(Duration.ofDays(180));
    assertThat(store().find("pi_scan3").createdAt())
        .isBefore(TIME.instant().minus(Duration.ofDays(30)));
    payments.put(
        "pi_scan3", Payment.paid().refunds(new Refund("re_late", 1200, "succeeded", "usd")));
    assertThat(request(glueUrl(), "POST", "/operations/reconciliations", null).statusCode())
        .isEqualTo(202);
    for (int i = 0; i < 3; i++) {
      glue.getBean(Reconciler.class).tick();
      worker().tick();
    }
    assertThat(get(erpUrl(), "/invoices/invoice_3").path("financialStatus").asText())
        .isEqualTo("REFUNDED");
  }

  @Test
  void exhaustedRetriesStayHeldUntilExplicitRequeueAndStaleVersionRevalidates() throws Exception {
    fixture("invoice", "pi_budget");
    fault("FAIL_BEFORE_WRITE", 100);
    event("evt_budget", "pi_budget");
    for (int i = 0; i < 5; i++) {
      worker().tick();
      if (i < 4) {
        due("pi_budget");
      }
    }
    state("pi_budget", "FAILED");
    assertThat(store().find("pi_budget").totalAttempts()).isEqualTo(5);
    store().enqueue("pi_budget");
    event("evt_cannot_reset", "pi_budget");
    worker().tick();
    state("pi_budget", "FAILED");
    assertThat(request(glueUrl(), "POST", "/operations/work/pi_budget/requeue", null).statusCode())
        .isEqualTo(202);
    fault("VERSION_CONFLICT", 1);
    worker().tick();
    state("pi_budget", "DONE");
    assertThat(store().find("pi_budget").transientFailures()).isZero();
  }

  @Test
  void stripeRateLimitRespectsRetryAfterWithoutLayeredSdkRetries() throws Exception {
    fixture("invoice", "pi_rate");
    event("evt_rate", "pi_rate");
    stripeFailure = 429;
    worker().tick();
    state("pi_rate", "RETRY_SCHEDULED");
    assertThat(store().find("pi_rate").nextEligibleAt()).isEqualTo(TIME.instant().plusSeconds(2));
    assertThat(stripeRequests.get()).isEqualTo(1);
    stripeFailure = 0;
    due("pi_rate");
    worker().tick();
    state("pi_rate", "DONE");
  }

  @Test
  void incompatibleAmountCurrencyAndMissingAssociationAreVisibleWithoutWrites() throws Exception {
    fixture("amount", "pi_amount");
    payments.put("pi_amount", new Payment(900, "usd", "succeeded", false, List.of()));
    event("evt_amount", "pi_amount");
    worker().tick();
    state("pi_amount", "NEEDS_REVIEW");
    assertThat(store().find("pi_amount").reasonCode()).isEqualTo("AMOUNT_MISMATCH");
    fixture("currency", "pi_currency");
    payments.put("pi_currency", new Payment(1200, "eur", "succeeded", false, List.of()));
    event("evt_currency", "pi_currency");
    worker().tick();
    state("pi_currency", "NEEDS_REVIEW");
    assertThat(store().find("pi_currency").reasonCode()).isEqualTo("CURRENCY_MISMATCH");
    payments.put("pi_unknown", Payment.paid());
    event("evt_unknown", "pi_unknown");
    worker().tick();
    state("pi_unknown", "NEEDS_REVIEW");
    assertThat(store().find("pi_unknown").reasonCode()).isEqualTo("ERP_HTTP_404");
    assertThat(store().find("pi_unknown").facts()).contains("1200");
    assertThat(get(erpUrl(), "/demo/state").path("appliedUpdateCount").asInt()).isZero();
  }

  @Test
  void refundNotificationsRefreshCurrentFactsAndFailedRefundEventuallyLeavesWaiting()
      throws Exception {
    fixture("invoice", "pi_refund_event");
    payments.put(
        "pi_refund_event",
        Payment.paid().refunds(new Refund("re_pending", 1200, "pending", "usd")));
    assertThat(
            event("evt_refund_created", "pi_refund_event", "refund.created", false, true)
                .statusCode())
        .isEqualTo(200);
    worker().tick();
    state("pi_refund_event", "WAITING_PROVIDER");
    payments.put(
        "pi_refund_event", Payment.paid().refunds(new Refund("re_pending", 1200, "failed", "usd")));
    due("pi_refund_event");
    worker().tick();
    state("pi_refund_event", "DONE");
    assertThat(get(erpUrl(), "/invoices/invoice").path("financialStatus").asText())
        .isEqualTo("PAID");
    assertThat(event("evt_ignored", "pi_refund_event", "customer.created", false, true).body())
        .contains("ignored");
    assertThat(store().find("pi_refund_event").totalAttempts()).isEqualTo(2);
  }

  @Test
  void retryScheduleAndBudgetSurviveRestart() throws Exception {
    fixture("invoice", "pi_retry_restart");
    fault("FAIL_BEFORE_WRITE", 1);
    event("evt_retry_restart", "pi_retry_restart");
    worker().tick();
    state("pi_retry_restart", "RETRY_SCHEDULED");
    var scheduled = store().find("pi_retry_restart");
    glue.close();
    startGlue();
    assertThat(store().find("pi_retry_restart").nextEligibleAt())
        .isEqualTo(scheduled.nextEligibleAt());
    assertThat(store().find("pi_retry_restart").transientFailures()).isEqualTo(1);
    worker().tick();
    assertThat(store().find("pi_retry_restart").totalAttempts()).isEqualTo(1);
    due("pi_retry_restart");
    worker().tick();
    state("pi_retry_restart", "DONE");
  }

  @Test
  void repeatingACompletionAfterAnUnknownCommitDoesNotConsumeAnotherRetry() throws Exception {
    fixture("invoice", "pi_completion");
    event("evt_completion", "pi_completion");
    var claim = store().claim();
    var outcome =
        new WorkStore.Outcome(WorkState.RETRY_SCHEDULED, "ERP_CONNECTION", null, null, null, 0);
    store().finish(claim, outcome);
    store().finish(claim, outcome);
    assertThat(store().find("pi_completion").transientFailures()).isEqualTo(1);
    assertThat(store().attempts("pi_completion", 100)).hasSize(1);
    due("pi_completion");
    worker().tick();
    state("pi_completion", "DONE");
  }

  @Test
  void singleWorkerRecoversAClaimWhoseCommitResponseWasLost() throws Exception {
    fixture("invoice", "pi_claim");
    event("evt_claim", "pi_claim");
    assertThat(store().claim()).isNotNull();
    worker().tick();
    state("pi_claim", "DONE");
    assertThat(store().attempts("pi_claim", 100))
        .extracting(AttemptView::outcome)
        .containsExactly(AttemptOutcome.DONE, AttemptOutcome.INTERRUPTED);
  }

  @Test
  void authenticationFailureIsHeldAndAttemptDoesNotClaimPriorObservedFacts() throws Exception {
    fixture("invoice", "pi_auth");
    event("evt_auth_first", "pi_auth");
    worker().tick();
    state("pi_auth", "DONE");
    assertThat(store().find("pi_auth").facts()).isNotNull();
    stripeFailure = 401;
    event("evt_auth_second", "pi_auth");
    worker().tick();
    state("pi_auth", "FAILED");
    assertThat(store().find("pi_auth").reasonCode()).isEqualTo("STRIPE_AUTHENTICATION");
    assertThat(store().attempts("pi_auth", 100).getFirst().facts()).isNull();
    assertThat(store().find("pi_auth").facts())
        .isNotNull(); // Last known job observation remains available.
    assertThat(get(erpUrl(), "/demo/state").path("appliedUpdateCount").asInt()).isEqualTo(1);
  }

  @Test
  void opaqueCursorsAndUnorderedInvoiceReferencesConvergeAcrossBatches() throws Exception {
    fixture("z_invoice", "pi_opaque_z");
    fixture("a_invoice", "pi_opaque_a");
    fixture("m_invoice", "pi_opaque_m");
    scriptedErpPages.put("", invoicePage("z_invoice", "opaque_z"));
    scriptedErpPages.put("opaque_z", invoicePage("a_invoice", "opaque_a"));
    scriptedErpPages.put("opaque_a", invoicePage("m_invoice", null));
    ReconciliationStore scans = glue.getBean(ReconciliationStore.class);
    var reconciliationRun = scans.request();
    assertThat(reconciliationMetric()).isZero();
    for (int batch = 0; batch < 3; batch++) {
      glue.getBean(Reconciler.class).tick();
      worker().tick();
    }
    var completed = scans.find(reconciliationRun.id());
    assertThat(completed.state()).isEqualTo(ReconciliationState.COMPLETE);
    assertThat(completed.pagesProcessed()).isEqualTo(3);
    assertThat(completed.enqueuedReferences()).isEqualTo(3);
    assertThat(reconciliationMetric()).isEqualTo(1);
    for (String paymentIntentId : List.of("pi_opaque_z", "pi_opaque_a", "pi_opaque_m")) {
      state(paymentIntentId, "DONE");
    }
  }

  @Test
  void nonAdvancingOpaqueCursorFailsWithoutCommittingPageWorkOrCheckpoint() throws Exception {
    fixture("first", "pi_cursor_first");
    fixture("second", "pi_cursor_second");
    scriptedErpPages.put("", invoicePage("first", "same_cursor"));
    scriptedErpPages.put("same_cursor", invoicePage("second", "same_cursor"));
    ReconciliationStore scans = glue.getBean(ReconciliationStore.class);
    var reconciliationRun = scans.request();
    glue.getBean(Reconciler.class).tick();
    glue.getBean(Reconciler.class).tick();
    var failed = scans.find(reconciliationRun.id());
    assertThat(failed.state()).isEqualTo(ReconciliationState.FAILED);
    assertThat(failed.lastFailure()).isEqualTo("ERP_INVALID_CURSOR");
    assertThat(failed.cursor()).isEqualTo("same_cursor");
    assertThat(failed.pagesProcessed()).isEqualTo(1);
    assertThat(failed.enqueuedReferences()).isEqualTo(1);
    assertThat(failed.completedAt()).isNull();
    assertThat(store().find("pi_cursor_second")).isNull();
    assertThat(reconciliationMetric()).isEqualTo(-1);
  }

  @Test
  void cyclicOpaqueEnumerationStopsAtPersistedPageBudgetAcrossRestart() throws Exception {
    fixture("first", "pi_cycle_first");
    fixture("second", "pi_cycle_second");
    scriptedErpPages.put("", invoicePage("first", "cycle_z"));
    scriptedErpPages.put("cycle_z", invoicePage("second", "cycle_a"));
    scriptedErpPages.put("cycle_a", invoicePage("first", "cycle_z"));
    glue.close();
    startGlue(2);
    try {
      ReconciliationStore scans = glue.getBean(ReconciliationStore.class);
      var reconciliationRun = scans.request();
      glue.getBean(Reconciler.class).tick();
      glue.close();
      startGlue(2);
      scans = glue.getBean(ReconciliationStore.class);
      glue.getBean(Reconciler.class).tick();
      glue.getBean(Reconciler.class).tick();
      var failed = scans.find(reconciliationRun.id());
      assertThat(failed.state()).isEqualTo(ReconciliationState.FAILED);
      assertThat(failed.lastFailure()).isEqualTo("ERP_ENUMERATION_LIMIT");
      assertThat(failed.pagesProcessed()).isEqualTo(2);
      assertThat(failed.cursor()).isEqualTo("cycle_a");
      assertThat(failed.completedAt()).isNull();
      assertThat(store().find("pi_cycle_first").requestedGeneration()).isEqualTo(1);
    } finally {
      glue.close();
      startGlue();
    }
  }

  @Test
  void databaseFailureRollsBackAReconciliationPageAndItsCheckpoint() throws Exception {
    fixture("invoice", "pi_scan_reject");
    ReconciliationStore scans = glue.getBean(ReconciliationStore.class);
    var reconciliationRun = scans.request();
    var jdbc = glue.getBean(JdbcTemplate.class);
    jdbc.execute(
        "ALTER TABLE glue.work_item ADD CONSTRAINT reject_scan_probe CHECK(payment_intent_id <> 'pi_scan_reject')");
    try {
      glue.getBean(Reconciler.class).tick();
      var failed = scans.find(reconciliationRun.id());
      assertThat(failed.state()).isEqualTo(ReconciliationState.FAILED);
      assertThat(failed.pagesProcessed()).isZero();
      assertThat(failed.enqueuedReferences()).isZero();
      assertThat(failed.cursor()).isNull();
      assertThat(store().find("pi_scan_reject")).isNull();
    } finally {
      jdbc.execute("ALTER TABLE glue.work_item DROP CONSTRAINT reject_scan_probe");
    }
    assertThat(scans.request().id()).isEqualTo(reconciliationRun.id());
    glue.getBean(Reconciler.class).tick();
    worker().tick();
    state("pi_scan_reject", "DONE");
    assertThat(scans.find(reconciliationRun.id()).state()).isEqualTo(ReconciliationState.COMPLETE);
  }

  @Test
  void startupRecoversAnIncompleteRunningReconciliationThroughTheManagedStore() throws Exception {
    fixture("invoice", "pi_scan_restart");
    ReconciliationStore scans = glue.getBean(ReconciliationStore.class);
    var reconciliationRun = scans.request();
    scans.claim();
    glue.close();
    startGlue();
    scans = glue.getBean(ReconciliationStore.class);
    assertThat(scans.find(reconciliationRun.id()).state())
        .isEqualTo(ReconciliationState.INCOMPLETE);
    glue.getBean(Reconciler.class).tick();
    worker().tick();
    state("pi_scan_restart", "DONE");
  }

  String invoicePage(String invoiceId, String nextCursor) throws Exception {
    Map<String, Object> page = new HashMap<>();
    page.put("items", List.of(get(erpUrl(), "/invoices/" + invoiceId)));
    if (nextCursor != null) {
      page.put("nextCursor", nextCursor);
    }
    return json.writeValueAsString(page);
  }

  double reconciliationMetric() throws Exception {
    return get(glueUrl(), "/actuator/metrics/sync.reconciliation.last.outcome")
        .path("measurements")
        .get(0)
        .path("value")
        .asDouble();
  }

  static Map<String, String> query(String raw) {
    var map = new HashMap<String, String>();
    if (raw == null) {
      return map;
    }
    for (String item : raw.split("&")) {
      var pair = item.split("=", 2);
      map.put(
          URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
          pair.length < 2 ? "" : URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
    }
    return map;
  }

  static class MutableClock extends Clock {
    volatile Instant now = Instant.parse("2026-10-23T00:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestClockConfiguration {
    @Bean
    @Primary
    Clock testClock() {
      return TIME;
    }
  }
}
