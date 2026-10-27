package com.example.fde.glue.config;

import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class RuntimeConfiguration {
  @Bean
  @Profile("!demo")
  URI stripeApiBase(@Value("${stripe.api-base:https://api.stripe.com}") URI apiBase) {
    return apiBase;
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
