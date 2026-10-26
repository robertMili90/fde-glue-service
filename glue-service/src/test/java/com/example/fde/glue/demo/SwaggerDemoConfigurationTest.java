package com.example.fde.glue.demo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.fde.glue.config.StripeProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;

class SwaggerDemoConfigurationTest {
  @Test
  void refusesOtherCredentialsEvenAgainstLocalProvider() {
    assertThatThrownBy(
            () ->
                new SwaggerDemoConfiguration(
                    new StripeProperties("sk_test_other_account", "whsec_other_secret"),
                    URI.create("http://127.0.0.1:12345")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("swagger-demo requires the local offline Stripe fixture");
  }

  @Test
  void refusesRemoteProviderEvenWithSyntheticCredentials() {
    assertThatThrownBy(
            () ->
                new SwaggerDemoConfiguration(
                    new StripeProperties("sk_test_offline_demo", "whsec_offline_demo"),
                    URI.create("https://api.stripe.com")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("swagger-demo requires the local offline Stripe fixture");
  }
}
