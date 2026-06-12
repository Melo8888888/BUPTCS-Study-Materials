package com.evcharge.service;

import com.evcharge.domain.ChargingDetail;
import com.evcharge.domain.ChargingPile;
import com.evcharge.domain.ChargingRequest;
import com.evcharge.domain.FaultEvent;
import com.evcharge.domain.StationState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public record StationStateData(
        LocalDate currentDate,
        LocalDateTime now,
        int fastSeq,
        int slowSeq,
        int detailSeq,
        int waitingCapacity,
        int pileSlotCapacity,
        int fastPileCount,
        int slowPileCount,
        BigDecimal parkingRate,
        int parkingGracePeriodMinutes,
        StationState stationState,
        Map<String, ChargingPile> piles,
        Map<String, ChargingRequest> requests,
        Deque<String> waitingFast,
        Deque<String> waitingSlow,
        Deque<String> redispatchFast,
        Deque<String> redispatchSlow,
        Deque<String> priorityFast,
        Deque<String> prioritySlow,
        List<ChargingDetail> details,
        List<FaultEvent> faultEvents
) {}
