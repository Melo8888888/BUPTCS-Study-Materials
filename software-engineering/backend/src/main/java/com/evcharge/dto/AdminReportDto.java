package com.evcharge.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminReportDto(
        LocalDateTime now,
        int requestCount,
        int activeRequestCount,
        int waitingCount,
        int chargingCount,
        int pileQueueCount,
        int completedCount,
        int paidCount,
        int faultPileCount,
        int detailCount,
        long totalDurationMinutes,
        BigDecimal totalKwh,
        BigDecimal chargeFee,
        BigDecimal serviceFee,
        BigDecimal totalFee,
        List<PileReportDto> piles
) {}
