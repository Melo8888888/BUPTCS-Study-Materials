package com.evcharge.service;

import com.evcharge.domain.ChargingDetail;
import com.evcharge.dto.ParkingFeeDto;
import com.evcharge.persistence.ParkingFeeRecord;
import com.evcharge.persistence.ParkingFeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ParkingFeeService {
    private final ParkingFeeRepository repository;
    private final StationService stationService;

    public ParkingFeeService(ParkingFeeRepository repository, StationService stationService) {
        this.repository = repository;
        this.stationService = stationService;
    }

    /**
     * Scan all existing charging details; for each detail whose vehicle has not yet been
     * "departed" (no paid fee row yet) and whose time-since-stop exceeds the grace period,
     * generate one ParkingFee row. Idempotent: at most one fee per detailNo.
     */
    @Transactional
    public List<ParkingFeeDto> scanAll() {
        LocalDateTime now = stationService.currentTime();
        BigDecimal rate = stationService.parkingRate();
        int grace = stationService.parkingGracePeriodMinutes();

        Set<String> existing = repository.findAll().stream()
                .map(ParkingFeeRecord::getBillDetailNo)
                .collect(Collectors.toCollection(HashSet::new));

        List<ParkingFeeDto> generated = new ArrayList<>();
        for (ChargingDetail detail : stationService.allDetails()) {
            if (detail.getStoppedAt() == null) continue;
            if (existing.contains(detail.getDetailNo())) continue;
            long overMinutes = computeOvertime(detail.getStoppedAt(), now, grace);
            if (overMinutes <= 0) continue;
            ParkingFeeRecord saved = repository.save(buildRecord(detail, overMinutes, rate, now));
            generated.add(toDto(saved));
        }
        return generated;
    }

    /**
     * Compute a (non-persisted) preview list for one vehicle: existing paid/unpaid records
     * plus any new fees that would be generated if scan ran now.
     */
    @Transactional(readOnly = true)
    public List<ParkingFeeDto> previewForVehicle(String vehicleId) {
        LocalDateTime now = stationService.currentTime();
        BigDecimal rate = stationService.parkingRate();
        int grace = stationService.parkingGracePeriodMinutes();

        Map<String, ParkingFeeRecord> byDetail = repository.findByVehicleIdOrderByGeneratedAtAscIdAsc(vehicleId).stream()
                .collect(Collectors.toMap(ParkingFeeRecord::getBillDetailNo, r -> r));

        List<ParkingFeeDto> result = new ArrayList<>();
        for (ChargingDetail detail : stationService.allDetails()) {
            if (!detail.getVehicleId().equals(vehicleId)) continue;
            ParkingFeeRecord persisted = byDetail.get(detail.getDetailNo());
            if (persisted != null) {
                result.add(toDto(persisted));
                continue;
            }
            if (detail.getStoppedAt() == null) continue;
            long overMinutes = computeOvertime(detail.getStoppedAt(), now, grace);
            if (overMinutes <= 0) continue;
            // Preview (no id): synthesize with a deterministic placeholder id `preview:<detailNo>`
            BigDecimal amount = rate.multiply(BigDecimal.valueOf(overMinutes)).setScale(2, RoundingMode.HALF_UP);
            result.add(new ParkingFeeDto(
                    "preview:" + detail.getDetailNo(),
                    detail.getVehicleId(),
                    detail.getPileId(),
                    detail.getDetailNo(),
                    overMinutes,
                    rate,
                    amount,
                    now,
                    false,
                    null
            ));
        }
        return result;
    }

    @Transactional
    public ParkingFeeDto payOne(String vehicleId, String feeId) {
        Optional<ParkingFeeRecord> maybe = repository.findById(feeId);
        // Allow "preview:<detailNo>" form to auto-generate then pay (one-shot pay path)
        if (maybe.isEmpty() && feeId.startsWith("preview:")) {
            String detailNo = feeId.substring("preview:".length());
            scanAll(); // generate any due rows including this one
            maybe = repository.findByBillDetailNo(detailNo);
        }
        ParkingFeeRecord record = maybe.orElseThrow(() -> new IllegalArgumentException("Parking fee not found: " + feeId));
        if (!record.getVehicleId().equalsIgnoreCase(vehicleId)) {
            throw new IllegalArgumentException("Parking fee does not belong to vehicle " + vehicleId);
        }
        if (record.isPaid()) {
            return toDto(record);
        }
        record.markPaid(stationService.currentTime());
        return toDto(repository.save(record));
    }

    @Transactional(readOnly = true)
    public List<ParkingFeeDto> listAll() {
        return repository.findAllByOrderByGeneratedAtAscIdAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ParkingFeeDto> listForVehicle(String vehicleId) {
        return repository.findByVehicleIdOrderByGeneratedAtAscIdAsc(vehicleId).stream().map(this::toDto).toList();
    }

    private long computeOvertime(LocalDateTime stoppedAt, LocalDateTime now, int graceMinutes) {
        long elapsed = Duration.between(stoppedAt, now).toMinutes();
        return Math.max(0, elapsed - graceMinutes);
    }

    private ParkingFeeRecord buildRecord(ChargingDetail detail, long overMinutes, BigDecimal rate, LocalDateTime now) {
        BigDecimal amount = rate.multiply(BigDecimal.valueOf(overMinutes)).setScale(2, RoundingMode.HALF_UP);
        return new ParkingFeeRecord(
                UUID.randomUUID().toString(),
                detail.getVehicleId(),
                detail.getPileId(),
                detail.getDetailNo(),
                overMinutes,
                rate,
                amount,
                now,
                false,
                null
        );
    }

    private ParkingFeeDto toDto(ParkingFeeRecord r) {
        return new ParkingFeeDto(
                r.getId(),
                r.getVehicleId(),
                r.getPileId(),
                r.getBillDetailNo(),
                r.getOverTimeMinutes(),
                r.getRatePerMinute(),
                r.getAmount(),
                r.getGeneratedAt(),
                r.isPaid(),
                r.getPaidAt()
        );
    }
}
