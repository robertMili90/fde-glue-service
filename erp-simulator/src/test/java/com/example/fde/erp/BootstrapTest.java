package com.example.fde.erp;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fde.erp.demo.DemoControls;
import com.example.fde.erp.demo.DemoState;
import com.example.fde.erp.invoice.CreateInvoice;
import com.example.fde.erp.invoice.ErpStore;
import com.example.fde.erp.invoice.PaymentProjection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class BootstrapTest {
  @TempDir Path directory;

  @Test
  void servesHealthAndOpenApiAndRetainsCommittedDataAcrossRestart() throws Exception {
    String marker = UUID.randomUUID().toString();
    int historyRows;
    try (var first = start()) {
      var jdbc = first.getBean(JdbcTemplate.class);
      jdbc.execute("CREATE TABLE erp.bootstrap_probe (marker VARCHAR(36) PRIMARY KEY)");
      historyRows =
          jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\"", Integer.class);
      jdbc.update("INSERT INTO erp.bootstrap_probe VALUES (?)", marker);
      var store = first.getBean(ErpStore.class);
      assertThat(store.create(new CreateInvoice("durable", 1200L, "USD", "pi_durable")).status())
          .isEqualTo(201);
      assertThat(
              store
                  .project(
                      "durable",
                      "durable_key",
                      1,
                      new PaymentProjection("pi_durable", "USD", 1200L, 0L, "PAID"))
                  .status())
          .isEqualTo(200);
      assertHttp(first, "/actuator/health", 200, "UP");
      assertHttp(first, "/v3/api-docs", 200, "ERP simulator");
      assertHttp(first, "/swagger-ui/index.html", 200, "Swagger UI");
    }
    // Close the whole application/pool, then reopen the same on-disk database.
    try (var second = start()) {
      var jdbc = second.getBean(JdbcTemplate.class);
      assertThat(jdbc.queryForObject("SELECT marker FROM erp.bootstrap_probe", String.class))
          .isEqualTo(marker);
      assertThat(
              jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\"", Integer.class))
          .isEqualTo(historyRows);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE",
                  Integer.class))
          .isEqualTo(1);
      assertHttp(second, "/invoices/durable", 200, "PAID");
      var store = second.getBean(ErpStore.class);
      assertThat(
              store
                  .project(
                      "durable",
                      "durable_key",
                      1,
                      new PaymentProjection("pi_durable", "USD", 1200L, 0L, "PAID"))
                  .status())
          .isEqualTo(200);
      assertThat(second.getBean(DemoControls.class).state()).isEqualTo(new DemoState(1, 1, 1));
      assertHttp(second, "/actuator/health", 200, "UP");
    }
  }

  private ServletWebServerApplicationContext start() {
    return (ServletWebServerApplicationContext)
        new SpringApplicationBuilder(ErpSimulatorApplication.class)
            .run(
                "--server.port=0",
                "--sync.enabled=false",
                "--spring.datasource.url=jdbc:h2:file:"
                    + directory.resolve("database")
                    + ";DB_CLOSE_ON_EXIT=FALSE",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN");
  }

  private void assertHttp(
      ServletWebServerApplicationContext context, String path, int status, String content)
      throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var request =
          HttpRequest.newBuilder(
                  URI.create("http://localhost:" + context.getWebServer().getPort() + path))
              .timeout(Duration.ofSeconds(10))
              .GET()
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(status);
      assertThat(response.body()).contains(content);
    }
  }
}
