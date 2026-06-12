package com.evcharge.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillDto(
        String detailNo,
        String vehicleId,
        String requestId,
        String pileId,
        BigDecimal chargedKwh,
        long durationMinutes,
        LocalDateTime startedAt,
        LocalDateTime stoppedAt,
        BigDecimal chargeFee,
        BigDecimal serviceFee,
        BigDecimal totalFee,
        String priceBreakdown,
        boolean paid
) {}
