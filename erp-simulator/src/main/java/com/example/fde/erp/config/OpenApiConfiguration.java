package com.example.fde.erp.config;

import com.example.fde.erp.invoice.InvoiceProblem;
import com.example.fde.erp.invoice.InvoiceView;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
  private final OpenAPI documentation;

  OpenApiConfiguration() throws IOException {
    try (InputStream source = new ClassPathResource("openapi/erp-api.yaml").getInputStream()) {
      documentation = Yaml.mapper().readValue(source, OpenAPI.class);
    }
  }

  @Bean
  OpenAPI openApi() {
    return new OpenAPI().info(documentation.getInfo()).tags(documentation.getTags());
  }

  @Bean
  OpenApiCustomizer operationDocumentation() {
    return generated -> {
      for (Map.Entry<String, PathItem> path : documentation.getPaths().entrySet()) {
        for (Map.Entry<HttpMethod, Operation> operation :
            path.getValue().readOperationsMap().entrySet()) {
          documentOperation(generated, path.getKey(), operation.getKey(), operation.getValue());
        }
      }
      // Springdoc can discard unreferenced models before this customizer runs.
      Components components = generated.getComponents();
      // These concrete response models are hidden behind HTTP response interfaces or exception
      // handlers.
      boolean openApi31 = generated.getOpenapi().startsWith("3.1");
      for (Class<?> model : List.of(InvoiceView.class, InvoiceProblem.class)) {
        ModelConverters.getInstance(openApi31).readAll(model).forEach(components::addSchemas);
      }
    };
  }

  private void documentOperation(
      OpenAPI generated, String path, HttpMethod method, Operation documented) {
    PathItem actualPath = generated.getPaths().get(path);
    Operation actualOperation =
        actualPath == null ? null : actualPath.readOperationsMap().get(method);
    if (actualOperation == null) {
      throw new IllegalStateException(
          "Swagger documentation has no matching endpoint: " + method + " " + path);
    }
    // Preserve discovered routes/operation IDs and DTO schemas; supply only the HTTP documentation.
    actualOperation.setSummary(documented.getSummary());
    actualOperation.setDescription(documented.getDescription());
    actualOperation.setTags(documented.getTags());
    actualOperation.setParameters(documented.getParameters());
    actualOperation.setRequestBody(documented.getRequestBody());
    actualOperation.setResponses(documented.getResponses());
  }
}
