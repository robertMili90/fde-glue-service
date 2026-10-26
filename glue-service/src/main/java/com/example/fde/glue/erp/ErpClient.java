package com.example.fde.glue.erp;

import com.example.fde.glue.config.SyncSettings;
import com.example.fde.glue.work.RetryAfter;
import com.example.fde.glue.work.SyncFailure;
import com.example.fde.glue.work.WorkState;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ErpClient {
  private enum Reason {
    ERP_INVALID_PAGE,
    ERP_INVALID_RESULT,
    ERP_CONNECTION,
    ERP_INTERRUPTED,
    ERP_INVALID_RESPONSE
  }

  private final String baseUrl;
  private final HttpClient http;
  private final ObjectMapper json;
  private final SyncSettings settings;
  private final Clock clock;

  public ErpClient(
      @Value("${erp.base-url}") String baseUrl,
      ObjectMapper json,
      SyncSettings settings,
      Clock clock) {
    this.baseUrl = baseUrl.replaceAll("/$", "");
    this.json = json;
    this.settings = settings;
    this.clock = clock;
    http =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(settings.httpTimeoutMs())).build();
  }

  private String segment(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  public InvoiceSnapshot find(String paymentIntentId) {
    return decode(
        send("/invoices/by-payment/" + segment(paymentIntentId), null, null, 0),
        InvoiceSnapshot.class);
  }

  public InvoicePage list(String cursor, int limit) {
    InvoicePage page =
        decode(
            send(
                "/invoices?limit=" + limit + (cursor == null ? "" : "&cursor=" + segment(cursor)),
                null,
                null,
                0),
            InvoicePage.class);
    if (page.items() == null || page.items().size() > limit) {
      throw new SyncFailure(WorkState.FAILED, Reason.ERP_INVALID_PAGE.name());
    }
    return page;
  }

  public boolean project(
      InvoiceSnapshot invoice, String operationKey, PaymentProjection projection) {
    String body =
        send(
            "/invoices/" + segment(invoice.id()) + "/payment-projection",
            projection,
            operationKey,
            invoice.version());
    if (body == null) {
      return false;
    }
    InvoiceSnapshot result = decode(body, InvoiceSnapshot.class);
    if (!invoice.id().equals(result.id())
        || !projection.paymentIntentId().equals(result.paymentIntentId())
        || !projection.equals(result.paymentProjection())
        || !projection.status().equals(result.financialStatus())
        || result.expectedAmountMinor() != invoice.expectedAmountMinor()
        || !invoice.currency().equals(result.currency())
        || result.version() < 1) {
      throw new SyncFailure(WorkState.FAILED, Reason.ERP_INVALID_RESULT.name());
    }
    return true;
  }

  private String send(String path, PaymentProjection body, String operationKey, long version) {
    try {
      var request =
          HttpRequest.newBuilder(URI.create(baseUrl + path))
              .timeout(Duration.ofMillis(settings.httpTimeoutMs()));
      if (body == null) {
        request.GET();
      } else {
        request
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", operationKey)
            .header("If-Match", "\"" + version + "\"")
            .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      }
      var response = exchange(request.build());
      if (response.statusCode() == 412 && body != null) {
        return null;
      }
      if (response.statusCode() != 200) {
        throw SyncFailure.http(
            "ERP",
            response.statusCode(),
            RetryAfter.millis(response.headers().firstValue("Retry-After").orElse(null), clock));
      }
      return response.body();
    } catch (IOException error) {
      throw new SyncFailure(WorkState.RETRY_SCHEDULED, Reason.ERP_CONNECTION.name(), error);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new SyncFailure(WorkState.RETRY_SCHEDULED, Reason.ERP_INTERRUPTED.name(), interrupted);
    }
  }

  private HttpResponse<String> exchange(HttpRequest request)
      throws IOException, InterruptedException {
    // Java 21's request timeout ends at response headers. The future completes
    // only after ofString consumes the body, so this deadline covers both.
    var response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    try {
      return response.get(settings.httpTimeoutMs(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException expired) {
      response.cancel(true);
      throw new HttpTimeoutException("ERP response deadline exceeded");
    } catch (InterruptedException interrupted) {
      response.cancel(true);
      throw interrupted;
    } catch (ExecutionException failed) {
      if (failed.getCause() instanceof IOException transportFailure) {
        throw transportFailure;
      }
      throw new IllegalStateException("ERP request failed unexpectedly", failed.getCause());
    }
  }

  @PreDestroy
  void shutdown() {
    http.shutdownNow();
  }

  private <T> T decode(String value, Class<T> type) {
    try {
      T decoded = json.readValue(value, type);
      if (decoded == null) {
        throw new SyncFailure(WorkState.FAILED, Reason.ERP_INVALID_RESPONSE.name());
      }
      return decoded;
    } catch (IOException error) {
      throw new SyncFailure(WorkState.FAILED, Reason.ERP_INVALID_RESPONSE.name(), error);
    }
  }
}
