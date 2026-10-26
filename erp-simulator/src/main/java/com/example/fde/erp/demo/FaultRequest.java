package com.example.fde.erp.demo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record FaultRequest(
    @NotNull @Pattern(regexp = "NONE|FAIL_BEFORE_WRITE|APPLY_THEN_DELAY|VERSION_CONFLICT")
        String mode,
    @Min(1) @Max(1000) Integer remainingRequests,
    @Min(0) @Max(10000) Integer delayMs) {}
