package com.example.fde.erp.invoice;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PaymentProjection(
    @NotNull @Pattern(regexp = "pi_[A-Za-z0-9_]+") @Size(max = 255) String paymentIntentId,
    @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
    @NotNull @Positive Long amountCollectedMinor,
    @NotNull @PositiveOrZero Long amountRefundedMinor,
    @NotNull @Pattern(regexp = "PAID|REFUNDED") String status) {}
