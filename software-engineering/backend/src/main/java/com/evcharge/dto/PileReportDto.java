package com.evcharge.dto;

import com.evcharge.domain.ChargeMode;
import com.evcharge.domain.PileStatus;

import java.math.BigDecimal;

public record PileReportDto(
        String pileId,
        ChargeMode mode,
        PileStatus status,
        String currentVehicle,
        int queuedCount,
        int completedDetailCount,
        long durationMinutes,
        BigDecimal chargedKwh,
        BigDecimal chargeFee,
        BigDecimal serviceFee,
        BigDecimal totalFee
) {}
