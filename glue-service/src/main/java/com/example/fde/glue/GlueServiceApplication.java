package com.example.fde.glue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GlueServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(GlueServiceApplication.class, args);
  }
}
