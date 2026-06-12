package com.evcharge.service;

import com.evcharge.domain.ChargingDetail;
import com.evcharge.domain.ChargeMode;
import com.evcharge.domain.FaultStrategy;
import com.evcharge.domain.PileStatus;
import com.evcharge.domain.RequestStatus;
import com.evcharge.dto.PileSnapshot;
import com.evcharge.dto.RequestSnapshot;
import com.evcharge.dto.SystemSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StationServiceTest {

    private final StationService stationService = new StationService(new BillingService());

    @Test
    void defaultAcceptanceFlowRunsThroughFaultsAndBilling() {
        runDefaultAcceptanceFlow();

        SystemSnapshot snapshot = stationService.snapshot();
        assertEquals(LocalDateTime.of(2026, 5, 12, 10, 50), snapshot.now());
        assertEquals(PileStatus.FAULT, pile(snapshot, "T1").status());
        assertEquals(PileStatus.FAULT, pile(snapshot, "F1").status());
        assertTrue(snapshot.waitingFast().isEmpty());

        ChargingDetail v21 = singleDetail("V21");
        assertEquals("F1", v21.getPileId());
        assertEquals(new BigDecimal("10.00"), v21.getChargedKwh());
        assertEquals(20, v21.getDurationMinutes());
        assertEquals(new BigDecimal("8.50"), v21.getChargeFee());
        assertEquals(new BigDecimal("8.00"), v21.getServiceFee());
        assertEquals(new BigDecimal("16.50"), v21.getTotalFee());
        assertTrue(v21.getPriceBreakdown().contains("09:50-10:00"));
        assertTrue(v21.getPriceBreakdown().contains("10:00-10:10"));
    }

    @Test
    void fastChargingAcrossNormalAndPeakPeriodsSplitsBill() {
        stationService.reset("09:50");
        stationService.submit("V100", com.evcharge.domain.ChargeMode.FAST, new BigDecimal("10"));
        stationService.advanceTo("10:10");

        ChargingDetail detail = singleDetail("V100");
        assertEquals(new BigDecimal("8.50"), detail.getChargeFee());
        assertEquals(new BigDecimal("8.00"), detail.getServiceFee());
        assertEquals(new BigDecimal("16.50"), detail.getTotalFee());
        assertEquals(20, detail.getDurationMinutes());
    }

    @Test
    void timeOrderFaultRedispatchDoesNotMixWithWaitingArea() {
        StationService service = new StationService(new BillingService());
        service.reset("06:00");
        service.submit("V1", ChargeMode.SLOW, new BigDecimal("100"));
        service.submit("V2", ChargeMode.SLOW, new BigDecimal("100"));
        service.submit("V3", ChargeMode.SLOW, new BigDecimal("10"));
        service.submit("V4", ChargeMode.SLOW, new BigDecimal("10"));
        service.submit("V5", ChargeMode.SLOW, new BigDecimal("10"));
        service.submit("V6", ChargeMode.SLOW, new BigDecimal("10"));
        service.submit("V7", ChargeMode.SLOW, new BigDecimal("10"));

        service.reportFault("T1", 60, FaultStrategy.TIME_ORDER);

        SystemSnapshot snapshot = service.snapshot();
        assertEquals(PileStatus.FAULT, pile(snapshot, "T1").status());
        assertEquals(List.of("V1", "V3"), pile(snapshot, "T2").queuedVehicles());
        assertEquals(List.of("V7"), snapshot.waitingSlow());
        assertEquals(RequestStatus.PILE_QUEUE, request(snapshot, "V1").status());
        assertEquals("T2", request(snapshot, "V1").pileId());
        assertEquals(RequestStatus.REDISPATCHING, request(snapshot, "V4").status());
        assertEquals(RequestStatus.REDISPATCHING, request(snapshot, "V5").status());
        assertEquals(RequestStatus.REDISPATCHING, request(snapshot, "V6").status());
        assertNull(request(snapshot, "V4").pileId());
        assertNull(request(snapshot, "V5").pileId());
        assertNull(request(snapshot, "V6").pileId());
    }

    @Test
    void faultedCurrentVehicleBillsPartialChargeAndWaitsForRedispatch() {
        StationService service = new StationService(new BillingService());
        service.reset("06:00");
        service.updateStationConfig(10, 1);
        service.submit("V1", ChargeMode.SLOW, new BigDecimal("20"));
        service.submit("V2", ChargeMode.SLOW, new BigDecimal("100"));
        service.advanceTo("06:10");

        service.reportFault("T1", 60, FaultStrategy.PRIORITY);

        SystemSnapshot snapshot = service.snapshot();
        RequestSnapshot v1 = request(snapshot, "V1");
        ChargingDetail detail = service.detailsFor("V1").get(0);
        assertEquals(RequestStatus.REDISPATCHING, v1.status());
        assertNull(v1.pileId());
        assertEquals(0, BigDecimal.ZERO.compareTo(v1.chargedKwh()));
        assertEquals(0, new BigDecimal("18.33333330").compareTo(v1.requestedKwh()));
        assertEquals("T1", detail.getPileId());
        assertEquals(new BigDecimal("1.67"), detail.getChargedKwh());
        assertEquals(10, detail.getDurationMinutes());
    }

    private void runDefaultAcceptanceFlow() {
        stationService.reset("06:00");
        for (AcceptanceEvent event : defaultEvents()) {
            stationService.advanceTo(event.time());
            stationService.applyAcceptanceEvent(event.action(), event.subject(), event.mode(), new BigDecimal(event.amount()));
        }
    }

    private ChargingDetail singleDetail(String vehicleId) {
        List<ChargingDetail> details = stationService.detailsFor(vehicleId);
        assertEquals(1, details.size());
        return details.get(0);
    }

    private PileSnapshot pile(SystemSnapshot snapshot, String id) {
        return snapshot.piles().stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private RequestSnapshot request(SystemSnapshot snapshot, String vehicleId) {
        return snapshot.requests().stream()
                .filter(r -> r.vehicleId().equals(vehicleId))
                .findFirst()
                .orElseThrow();
    }

    private List<AcceptanceEvent> defaultEvents() {
        return List.of(
                e("06:00", "A", "V1", "T", "40"),
                e("06:05", "A", "V2", "T", "30"),
                e("06:10", "A", "V3", "F", "60"),
                e("06:20", "A", "V2", "O", "0"),
                e("06:25", "A", "V4", "T", "20"),
                e("06:30", "A", "V5", "T", "20"),
                e("06:40", "A", "V6", "T", "20"),
                e("06:50", "A", "V7", "T", "10"),
                e("07:00", "A", "V8", "F", "90"),
                e("07:10", "A", "V9", "F", "30"),
                e("07:15", "A", "V10", "T", "10"),
                e("07:20", "A", "V11", "F", "60"),
                e("07:25", "A", "V12", "T", "10"),
                e("07:30", "A", "V13", "T", "7.5"),
                e("07:35", "A", "V14", "F", "75"),
                e("07:40", "A", "V15", "F", "45"),
                e("08:00", "A", "V16", "T", "5"),
                e("08:20", "A", "V17", "T", "15"),
                e("08:30", "A", "V18", "T", "20"),
                e("08:35", "A", "V19", "T", "25"),
                e("09:00", "A", "V20", "F", "30"),
                e("09:10", "A", "V7", "O", "0"),
                e("09:20", "A", "V11", "O", "0"),
                e("09:30", "A", "V18", "O", "0"),
                e("09:35", "A", "V20", "O", "0"),
                e("09:50", "A", "V21", "F", "30"),
                e("10:00", "A", "V22", "T", "10"),
                e("10:05", "C", "V19", "F", "25"),
                e("10:10", "C", "V21", "F", "10"),
                e("10:20", "C", "V22", "F", "10"),
                e("10:30", "B", "T1", "O", "60"),
                e("10:50", "B", "F1", "O", "120")
        );
    }

    private AcceptanceEvent e(String time, String action, String subject, String mode, String amount) {
        return new AcceptanceEvent(time, action, subject, mode, amount);
    }

    private record AcceptanceEvent(String time, String action, String subject, String mode, String amount) {}
}
