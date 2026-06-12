package com.evcharge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record VehicleChargeRequestDto(
        @NotBlank String mode,
        @NotNull @Positive BigDecimal amountKwh,
        BigDecimal batteryCapacity
) {}
