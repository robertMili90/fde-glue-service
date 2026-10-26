package com.example.fde.erp.invoice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateInvoice(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]+") @Size(max = 255) String id,
    @NotNull @Positive Long expectedAmountMinor,
    @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
    @NotNull @Pattern(regexp = "pi_[A-Za-z0-9_]+") @Size(max = 255) String paymentIntentId) {}
