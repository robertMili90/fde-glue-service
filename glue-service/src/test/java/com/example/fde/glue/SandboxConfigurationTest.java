package com.example.fde.glue;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fde.glue.config.SandboxConfiguration;
import com.example.fde.glue.config.StripeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SandboxConfigurationTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(PropertiesConfiguration.class, SandboxConfiguration.class);

  @ParameterizedTest
  @ValueSource(strings = {"", "sk_test_fixture", "rk_test_fixture"})
  void permitsMissingOrTestCredentials(String key) {
    context
        .withPropertyValues("stripe.secret-key=" + key)
        .run(app -> assertThat(app).hasNotFailed());
  }

  @ParameterizedTest
  @ValueSource(strings = {"sk_live_fixture", "rk_live_fixture", "unrecognized"})
  void rejectsLiveOrUnrecognizedCredentials(String key) {
    context
        .withPropertyValues("stripe.secret-key=" + key)
        .run(
            app -> {
              assertThat(app).hasFailed();
              assertThat(app.getStartupFailure())
                  .hasRootCauseMessage("Only Stripe test-mode secret keys are permitted");
              assertThat(app.getStartupFailure())
                  .hasStackTraceContaining("test-mode")
                  .hasStackTraceContaining("IllegalStateException");
              assertThat(app.getStartupFailure().toString()).doesNotContain(key);
            });
  }

  @Test
  void neverRendersCredentialsInPropertiesToString() {
    assertThat(new StripeProperties("sk_test_fixture", "whsec_fixture").toString())
        .doesNotContain("sk_test_fixture", "whsec_fixture");
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(StripeProperties.class)
  static class PropertiesConfiguration {}
}
