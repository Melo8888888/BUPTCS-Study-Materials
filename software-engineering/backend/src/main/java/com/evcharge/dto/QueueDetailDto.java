package com.evcharge.dto;

import com.evcharge.domain.ChargeMode;
import com.evcharge.domain.RequestStatus;

import java.math.BigDecimal;

public record QueueDetailDto(
        String vehicleId,
        String userId,
        ChargeMode mode,
        String queueNo,
        BigDecimal batteryCapacity,
        BigDecimal requestedKwh,
        long waitTimeMinutes,
        String location,
        RequestStatus status,
        String pileId
) {}
