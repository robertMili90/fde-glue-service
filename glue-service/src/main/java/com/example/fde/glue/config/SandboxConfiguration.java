package com.example.fde.glue.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SandboxConfiguration {
  @Bean
  InitializingBean sandboxCredentialsOnly(StripeProperties stripe) {
    return () -> {
      String key = stripe.secretKey();
      if (key != null && !key.isEmpty() && !key.matches("(?:sk|rk)_test_\\S+")) {
        // Do not include the supplied credential in binding errors or logs.
        throw new IllegalStateException("Only Stripe test-mode secret keys are permitted");
      }
    };
  }
}
