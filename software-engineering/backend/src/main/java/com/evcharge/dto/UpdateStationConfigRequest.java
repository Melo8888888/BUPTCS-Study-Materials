package com.evcharge.dto;

import java.math.BigDecimal;

public record UpdateStationConfigRequest(
        Integer waitingCapacity,
        Integer pileSlotCapacity,
        Integer fastPileCount,
        Integer slowPileCount,
        BigDecimal parkingRate,
        Integer parkingGracePeriodMinutes
) {}
