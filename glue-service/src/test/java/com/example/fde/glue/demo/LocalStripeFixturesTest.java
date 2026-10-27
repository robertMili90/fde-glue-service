package com.example.fde.glue.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LocalStripeFixturesTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void servesRegisteredTestFactsAndRefusesUnknownResourcesAndWrites() throws Exception {
    LocalStripeFixtures provider = new LocalStripeFixtures(json);
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
      JsonNode paid = read(client, provider, "/v1/payment_intents/pi_demo");
      assertThat(paid.path("id").asText()).isEqualTo("pi_demo");
      assertThat(paid.path("livemode").asBoolean()).isFalse();
      assertThat(paid.path("status").asText()).isEqualTo("succeeded");
      assertThat(paid.path("amount_received").asLong()).isEqualTo(1200);
      JsonNode unpaid = read(client, provider, "/v1/payment_intents/pi_unpaid");
      assertThat(unpaid.path("status").asText()).isEqualTo("requires_payment_method");
      assertThat(unpaid.path("amount_received").asLong()).isZero();
      JsonNode refunds = read(client, provider, "/v1/refunds?payment_intent=pi_refunded");
      assertThat(refunds.path("has_more").asBoolean()).isFalse();
      assertThat(refunds.path("data").size()).isEqualTo(1);
      assertThat(refunds.path("data").get(0).path("payment_intent").asText())
          .isEqualTo("pi_refunded");
      assertThat(refunds.path("data").get(0).path("amount").asLong()).isEqualTo(1200);
      assertThat(
              read(client, provider, "/v1/refunds?payment_intent=pi_demo").path("data").isEmpty())
          .isTrue();
      assertThat(request(client, provider, "/v1/payment_intents/pi_unknown", "GET").statusCode())
          .isEqualTo(404);
      assertThat(
              request(client, provider, "/v1/refunds/unknown?payment_intent=pi_demo", "GET")
                  .statusCode())
          .isEqualTo(404);
      assertThat(request(client, provider, "/v1/payment_intents/pi_demo", "POST").statusCode())
          .isEqualTo(405);
      assertThat(
              request(client, provider, "/v1/refunds?payment_intent=pi_demo", "POST").statusCode())
          .isEqualTo(405);
    } finally {
      provider.close();
    }
  }

  private JsonNode read(HttpClient client, LocalStripeFixtures provider, String path)
      throws Exception {
    var response = request(client, provider, path, "GET");
    assertThat(response.statusCode()).isEqualTo(200);
    return json.readTree(response.body());
  }

  private HttpResponse<String> request(
      HttpClient client, LocalStripeFixtures provider, String path, String method)
      throws Exception {
    return client.send(
        HttpRequest.newBuilder(provider.stripeApiBase().resolve(path))
            .timeout(Duration.ofSeconds(3))
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
