package com.evcharge.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record AcceptanceEventDto(
        @NotBlank String time,
        @NotBlank String action,
        @NotBlank String subject,
        String mode,
        BigDecimal amount,
        String strategy
) {}
