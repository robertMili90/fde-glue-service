package com.example.fde.glue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("stripe")
public record StripeProperties(String secretKey, String webhookSecret) {
  @Override
  public String toString() {
    return "StripeProperties[credentials redacted]";
  }
}
