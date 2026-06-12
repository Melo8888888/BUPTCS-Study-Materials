package com.evcharge.persistence;

import com.evcharge.domain.*;
import com.evcharge.service.StationStateData;
import com.evcharge.service.StationStateStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
public class JpaStationStateStore implements StationStateStore {
    private static final String STATE_ID = "main";

    private final StationStateRepository stateRepository;
    private final ChargingPileRepository pileRepository;
    private final ChargingRequestRepository requestRepository;
    private final ChargingDetailRepository detailRepository;
    private final FaultEventRepository faultEventRepository;

    public JpaStationStateStore(StationStateRepository stateRepository,
                                ChargingPileRepository pileRepository,
                                ChargingRequestRepository requestRepository,
                                ChargingDetailRepository detailRepository,
                                FaultEventRepository faultEventRepository) {
        this.stateRepository = stateRepository;
        this.pileRepository = pileRepository;
        this.requestRepository = requestRepository;
        this.detailRepository = detailRepository;
        this.faultEventRepository = faultEventRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StationStateData> load() {
        Optional<StationStateRecord> state = stateRepository.findById(STATE_ID);
        if (state.isEmpty()) return Optional.empty();

        Map<String, ChargingPile> piles = new LinkedHashMap<>();
        pileRepository.findAll().stream()
                .sorted(Comparator.comparing(ChargingPileRecord::getId))
                .forEach(record -> {
                    ChargingPile pile = new ChargingPile(record.getId(), record.getMode());
                    pile.setStatus(record.getStatus());
                    pile.setCurrentRequestId(record.getCurrentRequestId());
                    piles.put(pile.getId(), pile);
                });

        Map<String, ChargingRequest> requests = new LinkedHashMap<>();
        List<ChargingRequestRecord> requestRecords = requestRepository.findAll().stream()
                .sorted(Comparator.comparing(ChargingRequestRecord::getCreatedAt)
                        .thenComparing(ChargingRequestRecord::getQueueNo, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChargingRequestRecord::getId))
                .toList();
        for (ChargingRequestRecord record : requestRecords) {
            ChargingRequest request = new ChargingRequest(
                    record.getId(),
                    record.getVehicleId(),
                    record.getMode(),
                    record.getRequestedKwh(),
                    record.getQueueNo(),
                    record.getCreatedAt()
            );
            request.setStatus(record.getStatus());
            request.setPileId(record.getPileId());
            request.setStartedAt(record.getStartedAt());
            request.setStoppedAt(record.getStoppedAt());
            request.setChargedKwh(record.getChargedKwh());
            requests.put(request.getId(), request);
        }

        Deque<String> waitingFast = new ArrayDeque<>();
        Deque<String> waitingSlow = new ArrayDeque<>();
        Deque<String> redispatchFast = new ArrayDeque<>();
        Deque<String> redispatchSlow = new ArrayDeque<>();
        Deque<String> priorityFast = new ArrayDeque<>();
        Deque<String> prioritySlow = new ArrayDeque<>();

        requestRecords.stream()
                .sorted(Comparator.comparingInt(ChargingRequestRecord::getQueuePosition))
                .forEach(record -> {
                    QueueKind kind = record.getQueueKind() == null ? QueueKind.NONE : record.getQueueKind();
                    switch (kind) {
                        case WAITING_FAST -> waitingFast.addLast(record.getId());
                        case WAITING_SLOW -> waitingSlow.addLast(record.getId());
                        case REDISPATCH_FAST -> redispatchFast.addLast(record.getId());
                        case REDISPATCH_SLOW -> redispatchSlow.addLast(record.getId());
                        case PRIORITY_FAST -> priorityFast.addLast(record.getId());
                        case PRIORITY_SLOW -> prioritySlow.addLast(record.getId());
                        case PILE_QUEUE -> {
                            ChargingPile pile = piles.get(record.getPileId());
                            if (pile != null) pile.getQueue().add(record.getId());
                        }
                        case NONE -> {
                        }
                    }
                });

        List<ChargingDetail> details = detailRepository.findAllByOrderByGeneratedAtAscDetailNoAsc().stream()
                .map(this::toDetail)
                .toList();
        List<FaultEvent> faultEvents = faultEventRepository.findAllByOrderByStartedAtAscIdAsc().stream()
                .map(record -> new FaultEvent(record.getPileId(), record.getStartedAt(), record.getRecoverAt()))
                .toList();

        StationStateRecord record = state.get();
        return Optional.of(new StationStateData(
                record.getCurrentDate(),
                record.getNowTime(),
                record.getFastSeq(),
                record.getSlowSeq(),
                record.getDetailSeq(),
                record.getWaitingCapacity(),
                record.getPileSlotCapacity(),
                record.getFastPileCount() > 0 ? record.getFastPileCount() : 3,
                record.getSlowPileCount() > 0 ? record.getSlowPileCount() : 2,
                record.getParkingRate() != null ? record.getParkingRate() : new java.math.BigDecimal("0.5"),
                record.getParkingGracePeriodMinutes() >= 0 ? record.getParkingGracePeriodMinutes() : 5,
                record.getStationState() != null ? record.getStationState() : com.evcharge.domain.StationState.RUNNING,
                piles,
                requests,
                waitingFast,
                waitingSlow,
                redispatchFast,
                redispatchSlow,
                priorityFast,
                prioritySlow,
                new ArrayList<>(details),
                new ArrayList<>(faultEvents)
        ));
    }

    @Override
    @Transactional
    public void save(StationStateData state) {
        Map<String, QueueSlot> queueSlots = new HashMap<>();
        markQueue(queueSlots, state.waitingFast(), QueueKind.WAITING_FAST);
        markQueue(queueSlots, state.waitingSlow(), QueueKind.WAITING_SLOW);
        markQueue(queueSlots, state.redispatchFast(), QueueKind.REDISPATCH_FAST);
        markQueue(queueSlots, state.redispatchSlow(), QueueKind.REDISPATCH_SLOW);
        markQueue(queueSlots, state.priorityFast(), QueueKind.PRIORITY_FAST);
        markQueue(queueSlots, state.prioritySlow(), QueueKind.PRIORITY_SLOW);
        for (ChargingPile pile : state.piles().values()) {
            for (int i = 0; i < pile.getQueue().size(); i++) {
                queueSlots.put(pile.getQueue().get(i), new QueueSlot(QueueKind.PILE_QUEUE, i));
            }
        }

        faultEventRepository.deleteAllInBatch();
        detailRepository.deleteAllInBatch();
        requestRepository.deleteAllInBatch();
        pileRepository.deleteAllInBatch();
        stateRepository.deleteAllInBatch();

        stateRepository.save(new StationStateRecord(
                STATE_ID,
                state.currentDate(),
                state.now(),
                state.fastSeq(),
                state.slowSeq(),
                state.detailSeq(),
                state.waitingCapacity(),
                state.pileSlotCapacity(),
                state.fastPileCount(),
                state.slowPileCount(),
                state.parkingRate(),
                state.parkingGracePeriodMinutes(),
                state.stationState()
        ));
        pileRepository.saveAll(state.piles().values().stream().map(this::toPileRecord).toList());
        requestRepository.saveAll(state.requests().values().stream()
                .map(request -> toRequestRecord(request, queueSlots.getOrDefault(request.getId(), new QueueSlot(QueueKind.NONE, -1))))
                .toList());
        detailRepository.saveAll(state.details().stream().map(this::toDetailRecord).toList());
        faultEventRepository.saveAll(state.faultEvents().stream()
                .map(event -> new FaultEventRecord(event.getPileId(), event.getStartedAt(), event.getRecoverAt()))
                .toList());
    }

    private void markQueue(Map<String, QueueSlot> queueSlots, Deque<String> queue, QueueKind kind) {
        int position = 0;
        for (String requestId : queue) {
            queueSlots.put(requestId, new QueueSlot(kind, position++));
        }
    }

    private ChargingPileRecord toPileRecord(ChargingPile pile) {
        return new ChargingPileRecord(pile.getId(), pile.getMode(), pile.getPowerKw(), pile.getStatus(), pile.getCurrentRequestId());
    }

    private ChargingRequestRecord toRequestRecord(ChargingRequest request, QueueSlot slot) {
        return new ChargingRequestRecord(
                request.getId(),
                request.getVehicleId(),
                request.getMode(),
                request.getRequestedKwh(),
                request.getQueueNo(),
                request.getStatus(),
                request.getPileId(),
                request.getCreatedAt(),
                request.getStartedAt(),
                request.getStoppedAt(),
                request.getChargedKwh(),
                slot.kind(),
                slot.position()
        );
    }

    private ChargingDetailRecord toDetailRecord(ChargingDetail detail) {
        return new ChargingDetailRecord(
                detail.getDetailNo(),
                detail.getGeneratedAt(),
                detail.getPileId(),
                detail.getVehicleId(),
                detail.getRequestId(),
                detail.getChargedKwh(),
                detail.getDurationMinutes(),
                detail.getStartedAt(),
                detail.getStoppedAt(),
                detail.getChargeFee(),
                detail.getServiceFee(),
                detail.getTotalFee(),
                detail.getPriceBreakdown()
        );
    }

    private ChargingDetail toDetail(ChargingDetailRecord record) {
        ChargingDetail detail = new ChargingDetail();
        detail.setDetailNo(record.getDetailNo());
        detail.setGeneratedAt(record.getGeneratedAt());
        detail.setPileId(record.getPileId());
        detail.setVehicleId(record.getVehicleId());
        detail.setRequestId(record.getRequestId());
        detail.setChargedKwh(record.getChargedKwh());
        detail.setDurationMinutes(record.getDurationMinutes());
        detail.setStartedAt(record.getStartedAt());
        detail.setStoppedAt(record.getStoppedAt());
        detail.setChargeFee(record.getChargeFee());
        detail.setServiceFee(record.getServiceFee());
        detail.setTotalFee(record.getTotalFee());
        detail.setPriceBreakdown(record.getPriceBreakdown());
        return detail;
    }

    private record QueueSlot(QueueKind kind, int position) {}
}
