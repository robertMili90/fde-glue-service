package com.example.fde.glue.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
  @Bean
  OpenAPI openApi() {
    return new OpenAPI().info(new Info().title("Glue service operations").version("0.1.0"));
  }
}
