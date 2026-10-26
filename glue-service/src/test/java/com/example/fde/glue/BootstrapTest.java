package com.example.fde.glue;

import static org.assertj.core.api.Assertions.assertThat;

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
      jdbc.execute("CREATE TABLE glue.bootstrap_probe (marker VARCHAR(36) PRIMARY KEY)");
      historyRows =
          jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\"", Integer.class);
      jdbc.update("INSERT INTO glue.bootstrap_probe VALUES (?)", marker);
      assertHttp(first, "/actuator/health", 200, "UP");
      assertHttp(first, "/v3/api-docs", 200, "Glue service operations");
      assertHttp(first, "/swagger-ui/index.html", 200, "Swagger UI");
    }
    // Close the whole application/pool, then reopen the same on-disk database.
    try (var second = start()) {
      var jdbc = second.getBean(JdbcTemplate.class);
      assertThat(jdbc.queryForObject("SELECT marker FROM glue.bootstrap_probe", String.class))
          .isEqualTo(marker);
      assertThat(
              jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\"", Integer.class))
          .isEqualTo(historyRows);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE",
                  Integer.class))
          .isEqualTo(1);
      assertHttp(second, "/actuator/health", 200, "UP");
    }
  }

  private ServletWebServerApplicationContext start() {
    return (ServletWebServerApplicationContext)
        new SpringApplicationBuilder(GlueServiceApplication.class)
            .run(
                "--server.port=0",
                "--sync.enabled=false",
                "--spring.datasource.url=jdbc:h2:file:"
                    + directory.resolve("database")
                    + ";DB_CLOSE_ON_EXIT=FALSE",
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN",
                "--stripe.secret-key=",
                "--stripe.webhook-secret=");
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
