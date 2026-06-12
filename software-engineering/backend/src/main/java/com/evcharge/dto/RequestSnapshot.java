package com.evcharge.dto;

import com.evcharge.domain.ChargeMode;
import com.evcharge.domain.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RequestSnapshot(
        String id,
        String vehicleId,
        ChargeMode mode,
        BigDecimal requestedKwh,
        String queueNo,
        RequestStatus status,
        String pileId,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime stoppedAt,
        BigDecimal chargedKwh
) {}
