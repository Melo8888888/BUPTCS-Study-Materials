package com.evcharge.dto;

import java.math.BigDecimal;

public record StationConfigDto(
        int waitingCapacity,
        int pileSlotCapacity,
        int fastPileCount,
        int slowPileCount,
        int fastPowerKw,
        int slowPowerKw,
        BigDecimal parkingRate,
        int parkingGracePeriodMinutes
) {}
