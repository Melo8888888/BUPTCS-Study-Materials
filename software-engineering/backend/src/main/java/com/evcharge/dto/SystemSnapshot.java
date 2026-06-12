package com.evcharge.dto;

import com.evcharge.domain.ChargingDetail;
import com.evcharge.domain.StationState;

import java.time.LocalDateTime;
import java.util.List;

public record SystemSnapshot(
        LocalDateTime now,
        StationConfigDto stationConfig,
        StationState stationState,
        List<String> waitingFast,
        List<String> waitingSlow,
        List<PileSnapshot> piles,
        List<RequestSnapshot> requests,
        List<ChargingDetail> details
) {}
