package com.evcharge.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ParkingFeeDto(
        String id,
        String vehicleId,
        String pileId,
        String billDetailNo,
        long overTimeMinutes,
        BigDecimal ratePerMinute,
        BigDecimal amount,
        LocalDateTime generatedAt,
        boolean paid,
        LocalDateTime paidAt
) {}
