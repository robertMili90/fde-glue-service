package com.example.fde.glue.demo;

import com.example.fde.glue.config.StripeProperties;
import com.example.fde.glue.reconciliation.ReconciliationStore;
import com.example.fde.glue.work.WorkStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

@Configuration(proxyBeanMethods = false)
@Profile("swagger-demo")
class SwaggerDemoConfiguration {
  SwaggerDemoConfiguration(
      StripeProperties credentials, @Value("${stripe.api-base}") URI stripeApiBase) {
    // This profile exposes only a public, synthetic signing key in Swagger's browser code.
    if (!"sk_test_offline_demo".equals(credentials.secretKey())
        || !"whsec_offline_demo".equals(credentials.webhookSecret())
        || !"http".equals(stripeApiBase.getScheme())
        || stripeApiBase.getHost() == null
        || !Set.of("localhost", "127.0.0.1", "[::1]").contains(stripeApiBase.getHost())) {
      throw new IllegalArgumentException("swagger-demo requires the local offline Stripe fixture");
    }
  }

  @Bean
  @Order(100)
  OpenApiCustomizer seededDemoDocumentation(
      ReconciliationStore reconciliations, WorkStore work, ObjectMapper json) throws IOException {
    OpenAPI documentation;
    try (var source = new ClassPathResource("openapi/swagger-demo.yaml").getInputStream()) {
      documentation = Yaml.mapper().readValue(source, OpenAPI.class);
    }
    return generated -> {
      generated.getInfo().setDescription(documentation.getInfo().getDescription());
      var runs = reconciliations.list(1);
      if (!runs.isEmpty()) {
        generated
            .getPaths()
            .get("/operations/reconciliations/{id}")
            .getGet()
            .getParameters()
            .stream()
            .filter(parameter -> parameter.getName().equals("id"))
            .forEach(parameter -> parameter.setExample(runs.getFirst().id()));
        snapshot(generated.getPaths().get("/operations/reconciliations").getGet(), "200", runs);
        snapshot(
            generated.getPaths().get("/operations/reconciliations/{id}").getGet(),
            "200",
            runs.getFirst());
        // A queued response illustrates this existing run's initial state, with its real ID.
        var queuedExamples =
            generated
                .getPaths()
                .get("/operations/reconciliations")
                .getPost()
                .getResponses()
                .get("202")
                .getContent()
                .get("application/json")
                .getExamples();
        queuedExamples
            .values()
            .forEach(
                example -> {
                  ObjectNode queued = json.valueToTree(example.getValue());
                  queued.put("id", runs.getFirst().id());
                  example.setValue(queued);
                });
      }
      var demoWork = work.find("pi_demo");
      if (demoWork != null) {
        snapshot(generated.getPaths().get("/operations/work").getGet(), "200", work.list("", 20));
        snapshot(
            generated.getPaths().get("/operations/work/{paymentIntentId}").getGet(),
            "200",
            demoWork);
        snapshot(
            generated.getPaths().get("/operations/work/{paymentIntentId}/requeue").getPost(),
            "202",
            demoWork);
        snapshot(
            generated.getPaths().get("/operations/work/{paymentIntentId}/attempts").getGet(),
            "200",
            work.attempts("pi_demo", 20));
      }
      var webhook = generated.getPaths().get("/webhooks/stripe").getPost();
      var demoWebhook = documentation.getPaths().get("/webhooks/stripe").getPost();
      webhook.setDescription(demoWebhook.getDescription());
      webhook.getParameters().getFirst().setExample("signed-automatically-in-local-demo");
      webhook
          .getRequestBody()
          .getContent()
          .get("application/json")
          .getExamples()
          .put(
              "Refund updated",
              demoWebhook
                  .getRequestBody()
                  .getContent()
                  .get("application/json")
                  .getExamples()
                  .get("Refund updated"));
    };
  }

  private void snapshot(Operation operation, String status, Object value) {
    operation
        .getResponses()
        .get(status)
        .getContent()
        .get("application/json")
        .setExamples(Map.of("Current database snapshot", new Example().value(value)));
  }

  @Bean
  SwaggerIndexTransformer demoSigningTransformer(
      SwaggerUiConfigProperties ui,
      SwaggerUiOAuthProperties oauth,
      SwaggerWelcomeCommon welcome,
      ObjectMapperProvider mapper)
      throws IOException {
    String signingScript =
        new ClassPathResource("openapi/swagger-demo-signing.js")
            .getContentAsString(StandardCharsets.UTF_8);
    return new SwaggerIndexPageTransformer(ui, oauth, welcome, mapper) {
      @Override
      public Resource transform(
          HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
          throws IOException {
        Resource transformed = super.transform(request, resource, chain);
        if ("swagger-initializer.js".equals(resource.getFilename())) {
          String initializer = transformed.getContentAsString(StandardCharsets.UTF_8);
          // Set the hook when SwaggerUIBundle is constructed, before any requests run.
          return new TransformedResource(
              transformed,
              (signingScript
                      + "\n"
                      + initializer.replace(
                          "SwaggerUIBundle({",
                          "SwaggerUIBundle({\nrequestInterceptor: window.fdeDemoSign,"))
                  .getBytes(StandardCharsets.UTF_8));
        }
        return transformed;
      }
    };
  }
}
