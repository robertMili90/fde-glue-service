package com.example.fde.glue.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("sync")
public record SyncSettings(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("5") @Min(1) @Max(20) int maxAttempts,
    @DefaultValue("1000") @Min(1) @Max(60000) long retryBaseMs,
    @DefaultValue("60000") @Min(1) @Max(60000) long retryMaxMs,
    @DefaultValue("30000") @Min(1) long providerPollMs,
    @DefaultValue("2000") @Min(1) @Max(30000) int httpTimeoutMs,
    @DefaultValue("100") @Min(1) @Max(100) int reconciliationPageSize,
    @DefaultValue("10") @Min(1) @Max(100) int reconciliationBatchPages,
    @DefaultValue("10000") @Min(1) @Max(1000000) int reconciliationMaxPages) {}
