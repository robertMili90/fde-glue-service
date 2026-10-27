package com.example.fde.glue.demo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration(proxyBeanMethods = false)
@Profile("demo")
class LocalStripeFixtures {
  private record PaymentIntent(
      String id,
      String object,
      long amount,
      @JsonProperty("amount_received") long amountReceived,
      String currency,
      String status,
      boolean livemode,
      @JsonProperty("capture_method") String captureMethod) {}

  private record Refund(
      String id,
      String object,
      @JsonProperty("payment_intent") String paymentIntentId,
      long amount,
      String currency,
      String status) {}

  private record RefundPage(
      String object, String url, List<Refund> data, @JsonProperty("has_more") boolean hasMore) {}

  private record ErrorResponse(Problem error) {}

  private record Problem(String type, String code, String message) {}

  private static final Logger log = LoggerFactory.getLogger(LocalStripeFixtures.class);
  private static final Set<String> PAYMENT_IDS =
      Set.of("pi_demo", "pi_refunded", "pi_review", "pi_unpaid", "pi_create");

  private final ObjectMapper json;
  private final HttpServer server;

  LocalStripeFixtures(ObjectMapper json) throws IOException {
    this.json = json;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/payment_intents/", this::payment);
    server.createContext("/v1/refunds", this::refunds);
    server.start();
    log.info("Local demo active: read-only synthetic Stripe fixtures; no Stripe network access");
  }

  @Bean
  URI stripeApiBase() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  private void payment(HttpExchange exchange) throws IOException {
    String paymentIntentId =
        exchange.getRequestURI().getPath().substring("/v1/payment_intents/".length());
    if (!supportedRead(exchange, paymentIntentId)) {
      return;
    }
    boolean unpaid = paymentIntentId.equals("pi_unpaid");
    String status = unpaid ? "requires_payment_method" : "succeeded";
    send(
        exchange,
        200,
        new PaymentIntent(
            paymentIntentId,
            "payment_intent",
            1200,
            unpaid ? 0 : 1200,
            "usd",
            status,
            false,
            "automatic"));
  }

  private void refunds(HttpExchange exchange) throws IOException {
    String paymentIntentId = null;
    if (exchange.getRequestURI().getPath().equals("/v1/refunds")) {
      paymentIntentId =
          UriComponentsBuilder.fromUri(exchange.getRequestURI())
              .build()
              .getQueryParams()
              .getFirst("payment_intent");
    }
    if (!supportedRead(exchange, paymentIntentId)) {
      return;
    }
    List<Refund> refunds = List.of();
    if (paymentIntentId.equals("pi_refunded")) {
      refunds =
          List.of(
              new Refund("re_pi_refunded", "refund", paymentIntentId, 1200, "usd", "succeeded"));
    }
    send(exchange, 200, new RefundPage("list", "/v1/refunds", refunds, false));
  }

  private boolean supportedRead(HttpExchange exchange, String paymentIntentId) throws IOException {
    if (!exchange.getRequestMethod().equals("GET")) {
      send(
          exchange,
          405,
          new ErrorResponse(
              new Problem(
                  "invalid_request_error", "method_not_allowed", "Read-only local demo provider")));
      return false;
    }
    if (paymentIntentId == null || !PAYMENT_IDS.contains(paymentIntentId)) {
      send(
          exchange,
          404,
          new ErrorResponse(
              new Problem(
                  "invalid_request_error",
                  "resource_missing",
                  "No such local demo PaymentIntent")));
      return false;
    }
    return true;
  }

  private void send(HttpExchange exchange, int status, Object response) throws IOException {
    try (exchange) {
      byte[] body = json.writeValueAsBytes(response);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, body.length);
      exchange.getResponseBody().write(body);
    }
  }

  @PreDestroy
  public void close() {
    server.stop(0);
  }
}
