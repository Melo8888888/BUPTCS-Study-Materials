package com.evcharge.service;

import com.evcharge.domain.ChargingDetail;
import com.evcharge.domain.ChargingRequest;
import com.evcharge.domain.RequestStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BillingService {
    private static final BigDecimal SERVICE_RATE = new BigDecimal("0.8");
    private final AtomicInteger detailSeq = new AtomicInteger(1);

    public ChargingDetail createDetail(ChargingRequest request, String pileId, LocalDateTime generatedAt, RequestStatus status) {
        long durationMinutes = Math.max(0, Duration.between(request.getStartedAt(), request.getStoppedAt()).toMinutes());
        BigDecimal charged = request.getChargedKwh().setScale(4, RoundingMode.HALF_UP);
        FeeResult fee = calculateFee(request.getStartedAt(), request.getStoppedAt(), charged);

        ChargingDetail detail = new ChargingDetail();
        detail.setDetailNo("D" + generatedAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + String.format("%05d", detailSeq.getAndIncrement()));
        detail.setGeneratedAt(generatedAt);
        detail.setPileId(pileId);
        detail.setVehicleId(request.getVehicleId());
        detail.setRequestId(request.getId());
        detail.setChargedKwh(charged.setScale(2, RoundingMode.HALF_UP));
        detail.setDurationMinutes(durationMinutes);
        detail.setStartedAt(request.getStartedAt());
        detail.setStoppedAt(request.getStoppedAt());
        detail.setChargeFee(fee.chargeFee());
        detail.setServiceFee(fee.serviceFee());
        detail.setTotalFee(fee.totalFee());
        detail.setPriceBreakdown(fee.breakdown());
        detail.setStatus(status);
        return detail;
    }

    public FeeResult calculateFee(LocalDateTime start, LocalDateTime end, BigDecimal chargedKwh) {
        long totalMinutes = Math.max(1, Duration.between(start, end).toMinutes());
        LocalDateTime cursor = start;
        BigDecimal chargeFee = BigDecimal.ZERO;
        List<String> parts = new ArrayList<>();

        while (cursor.isBefore(end)) {
            LocalDateTime next = nextBoundary(cursor);
            if (next.isAfter(end)) next = end;
            long minutes = Math.max(0, Duration.between(cursor, next).toMinutes());
            BigDecimal kwh = chargedKwh
                    .multiply(BigDecimal.valueOf(minutes))
                    .divide(BigDecimal.valueOf(totalMinutes), 8, RoundingMode.HALF_UP);
            BigDecimal rate = rateAt(cursor.toLocalTime());
            BigDecimal partFee = kwh.multiply(rate);
            chargeFee = chargeFee.add(partFee);
            parts.add(cursor.toLocalTime() + "-" + next.toLocalTime() + " " + labelAt(cursor.toLocalTime()) + " " + kwh.setScale(4, RoundingMode.HALF_UP) + "度");
            cursor = next;
        }

        BigDecimal serviceFee = chargedKwh.multiply(SERVICE_RATE);
        BigDecimal roundedCharge = chargeFee.setScale(2, RoundingMode.HALF_UP);
        BigDecimal roundedService = serviceFee.setScale(2, RoundingMode.HALF_UP);
        return new FeeResult(roundedCharge, roundedService, roundedCharge.add(roundedService).setScale(2, RoundingMode.HALF_UP), String.join("; ", parts));
    }

    public void resetDetailSeq(int nextValue) {
        detailSeq.set(Math.max(1, nextValue));
    }

    private LocalDateTime nextBoundary(LocalDateTime time) {
        LocalTime t = time.toLocalTime();
        LocalTime[] boundaries = {
                LocalTime.of(7, 0), LocalTime.of(10, 0), LocalTime.of(15, 0),
                LocalTime.of(18, 0), LocalTime.of(21, 0), LocalTime.of(23, 0)
        };
        for (LocalTime boundary : boundaries) {
            if (t.isBefore(boundary)) return time.toLocalDate().atTime(boundary);
        }
        return time.toLocalDate().plusDays(1).atTime(7, 0);
    }

    private BigDecimal rateAt(LocalTime time) {
        if ((!time.isBefore(LocalTime.of(10, 0)) && time.isBefore(LocalTime.of(15, 0)))
                || (!time.isBefore(LocalTime.of(18, 0)) && time.isBefore(LocalTime.of(21, 0)))) {
            return new BigDecimal("1.0");
        }
        if ((!time.isBefore(LocalTime.of(7, 0)) && time.isBefore(LocalTime.of(10, 0)))
                || (!time.isBefore(LocalTime.of(15, 0)) && time.isBefore(LocalTime.of(18, 0)))
                || (!time.isBefore(LocalTime.of(21, 0)) && time.isBefore(LocalTime.of(23, 0)))) {
            return new BigDecimal("0.7");
        }
        return new BigDecimal("0.4");
    }

    private String labelAt(LocalTime time) {
        BigDecimal rate = rateAt(time);
        if (rate.compareTo(new BigDecimal("1.0")) == 0) return "峰";
        if (rate.compareTo(new BigDecimal("0.7")) == 0) return "平";
        return "谷";
    }

    public record FeeResult(BigDecimal chargeFee, BigDecimal serviceFee, BigDecimal totalFee, String breakdown) {}
}
