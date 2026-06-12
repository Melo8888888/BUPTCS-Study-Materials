package com.evcharge.dto;

import com.evcharge.domain.ChargeMode;
import com.evcharge.domain.PileStatus;

import java.util.List;

public record PileSnapshot(
        String id,
        ChargeMode mode,
        int powerKw,
        PileStatus status,
        String currentVehicle,
        List<String> queuedVehicles
) {}
