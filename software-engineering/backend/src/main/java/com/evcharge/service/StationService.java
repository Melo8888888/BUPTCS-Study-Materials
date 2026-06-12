package com.evcharge.service;

import com.evcharge.domain.*;
import com.evcharge.dto.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class StationService {
    private static final int DEFAULT_WAITING_CAPACITY = 10;
    private static final int DEFAULT_PILE_SLOT_CAPACITY = 3;
    private static final int DEFAULT_FAST_PILE_COUNT = 3;
    private static final int DEFAULT_SLOW_PILE_COUNT = 2;
    private static final int FAST_POWER_KW = 30;
    private static final int SLOW_POWER_KW = 10;
    private static final int MAX_PILE_COUNT_PER_MODE = 20;
    private static final BigDecimal DEFAULT_PARKING_RATE = new BigDecimal("0.5");
    private static final int DEFAULT_PARKING_GRACE_MINUTES = 5;
    private final BillingService billingService;
    private final StationStateStore stateStore;
    private final StationEventPublisher eventPublisher;
    private final ReentrantLock lock = new ReentrantLock();

    private final Map<String, ChargingPile> piles = new LinkedHashMap<>();
    private final Map<String, ChargingRequest> requests = new LinkedHashMap<>();
    private final Deque<String> waitingFast = new ArrayDeque<>();
    private final Deque<String> waitingSlow = new ArrayDeque<>();
    private final Deque<String> redispatchFast = new ArrayDeque<>();
    private final Deque<String> redispatchSlow = new ArrayDeque<>();
    private final Deque<String> priorityFast = new ArrayDeque<>();
    private final Deque<String> prioritySlow = new ArrayDeque<>();
    private final List<ChargingDetail> details = new ArrayList<>();
    private final List<FaultEvent> faultEvents = new ArrayList<>();
    private LocalDate currentDate = LocalDate.of(2026, 5, 12);
    private LocalDateTime now = currentDate.atTime(6, 0);
    private int fastSeq = 1;
    private int slowSeq = 1;
    private int waitingCapacity = DEFAULT_WAITING_CAPACITY;
    private int pileSlotCapacity = DEFAULT_PILE_SLOT_CAPACITY;
    private int fastPileCount = DEFAULT_FAST_PILE_COUNT;
    private int slowPileCount = DEFAULT_SLOW_PILE_COUNT;
    private BigDecimal parkingRate = DEFAULT_PARKING_RATE;
    private int parkingGracePeriodMinutes = DEFAULT_PARKING_GRACE_MINUTES;
    private StationState stationState = StationState.RUNNING;

    public StationService(BillingService billingService) {
        this.billingService = billingService;
        this.stateStore = null;
        this.eventPublisher = null;
        reset("06:00");
    }

    @Autowired
    public StationService(BillingService billingService,
                          ObjectProvider<StationStateStore> stateStoreProvider,
                          ObjectProvider<StationEventPublisher> eventPublisherProvider,
                          @org.springframework.beans.factory.annotation.Value("${evcharge.parking.ratePerMinute:0.5}") BigDecimal parkingRate,
                          @org.springframework.beans.factory.annotation.Value("${evcharge.parking.gracePeriodMinutes:5}") int parkingGracePeriodMinutes) {
        this.billingService = billingService;
        this.stateStore = stateStoreProvider.getIfAvailable();
        this.eventPublisher = eventPublisherProvider.getIfAvailable();
        this.parkingRate = parkingRate == null ? DEFAULT_PARKING_RATE : parkingRate;
        this.parkingGracePeriodMinutes = parkingGracePeriodMinutes;
        if (!restoreState()) {
            reset("06:00");
        }
    }

    public ChargingRequest submit(String vehicleId, ChargeMode mode, BigDecimal amountKwh) {
        return submit(vehicleId, mode, amountKwh, null);
    }

    public ChargingRequest submit(String vehicleId, ChargeMode mode, BigDecimal amountKwh, BigDecimal batteryCapacity) {
        lock.lock();
        try {
            ensureStationRunning();
            ensureNoActiveRequest(vehicleId);
            ensureWaitingCapacity();
            ChargingRequest request = new ChargingRequest(vehicleId, mode, amountKwh, nextQueueNo(mode), now);
            if (batteryCapacity != null) request.setBatteryCapacity(batteryCapacity);
            requests.put(request.getId(), request);
            waitingQueue(mode).addLast(request.getId());
            schedule();
            saveAndPublish("REQUEST_SUBMITTED");
            return request;
        } finally {
            lock.unlock();
        }
    }

    public ChargingRequest change(String vehicleId, ChargeMode mode, BigDecimal amountKwh) {
        lock.lock();
        try {
            ChargingRequest request = activeRequest(vehicleId);
            // 需求 PDF 第 6 条：充电中不允许修改请求；请先取消，再重新进入等候区。
            if (request.getStatus() == RequestStatus.CHARGING
                    || request.getStatus() == RequestStatus.PILE_QUEUE) {
                throw new IllegalArgumentException(
                        "充电中或已进入桩内队列时不允许修改请求；如需调整，请先取消并重新申请");
            }
            boolean modeChanged = request.getMode() != mode;
            if (modeChanged) {
                // 需求 PDF § 6-a-1：改充电模式 → 重新生成排队号，排到新模式队列最后一位。
                removeFromAllQueues(request.getId());
                request.setMode(mode);
                request.setRequestedKwh(amountKwh);
                request.setQueueNo(nextQueueNo(mode));
                request.setPileId(null);
                request.setStatus(RequestStatus.WAITING_AREA);
                waitingQueue(mode).addLast(request.getId());
            } else {
                // 需求 PDF § 6-b-1：仅改充电量 → 排队号不变、原队列位置不变。
                request.setRequestedKwh(amountKwh);
            }
            schedule();
            saveAndPublish("REQUEST_CHANGED");
            return request;
        } finally {
            lock.unlock();
        }
    }

    public ChargingRequest cancel(String vehicleId) {
        lock.lock();
        try {
            ChargingRequest request = activeRequest(vehicleId);
            if (request.getStatus() == RequestStatus.CHARGING) {
                finishCharging(request, RequestStatus.CANCELLED);
                startNextIfPossible(request.getPileId());
            } else {
                removeFromAllQueues(request.getId());
                request.setStatus(RequestStatus.CANCELLED);
            }
            schedule();
            saveAndPublish("REQUEST_CANCELLED");
            return request;
        } finally {
            lock.unlock();
        }
    }

    public ChargingRequest end(String vehicleId) {
        lock.lock();
        try {
            ChargingRequest request = activeRequest(vehicleId);
            if (request.getStatus() != RequestStatus.CHARGING) {
                throw new IllegalArgumentException("Vehicle is not charging: " + vehicleId);
            }
            finishCharging(request, RequestStatus.COMPLETED);
            startNextIfPossible(request.getPileId());
            schedule();
            saveAndPublish("CHARGING_ENDED");
            return request;
        } finally {
            lock.unlock();
        }
    }

    public PaymentResultDto pay(String vehicleId) {
        lock.lock();
        try {
            requests.values().stream()
                    .filter(r -> r.getVehicleId().equals(vehicleId))
                    .filter(r -> r.getStatus() == RequestStatus.COMPLETED
                            || r.getStatus() == RequestStatus.INTERRUPTED
                            || r.getStatus() == RequestStatus.CANCELLED)
                    .forEach(r -> r.setStatus(RequestStatus.PAID));
            List<BillDto> bills = billsForVehicle(vehicleId);
            BigDecimal paidAmount = bills.stream().map(BillDto::totalFee).reduce(BigDecimal.ZERO, BigDecimal::add);
            saveAndPublish("BILL_PAID");
            return new PaymentResultDto(vehicleId, paidAmount, bills.size(), bills);
        } finally {
            lock.unlock();
        }
    }

    public void reportFault(String pileId, long minutes) {
        reportFault(pileId, minutes, FaultStrategy.PRIORITY);
    }

    public void reportFault(String pileId, long minutes, FaultStrategy strategy) {
        lock.lock();
        try {
            ChargingPile pile = pile(pileId);
            FaultStrategy chosen = strategy == null ? FaultStrategy.PRIORITY : strategy;
            pile.setStatus(PileStatus.FAULT);
            faultEvents.add(new FaultEvent(pileId, now, now.plusMinutes(minutes)));

            String interruptedCurrentId = null;
            if (pile.getCurrentRequestId() != null) {
                interruptedCurrentId = interruptCurrentForRedispatch(pile);
            }

            if (chosen == FaultStrategy.PRIORITY) {
                Deque<String> priority = priorityQueue(pile.getMode());
                if (interruptedCurrentId != null) {
                    priority.addLast(interruptedCurrentId);
                }
                for (String queuedId : new ArrayList<>(pile.getQueue())) {
                    ChargingRequest queued = requests.get(queuedId);
                    queued.setPileId(null);
                    queued.setStatus(RequestStatus.REDISPATCHING);
                    priority.addLast(queuedId);
                }
                pile.getQueue().clear();
            } else { // TIME_ORDER
                List<ChargingPile> peers = piles.values().stream()
                        .filter(p -> p.getMode() == pile.getMode()
                                && !p.getId().equals(pileId)
                                && p.getStatus() == PileStatus.WORKING)
                        .toList();
                List<String> pool = new ArrayList<>(pile.getQueue());
                if (interruptedCurrentId != null) {
                    pool.add(interruptedCurrentId);
                }
                pile.getQueue().clear();
                for (ChargingPile peer : peers) {
                    pool.addAll(peer.getQueue());
                    peer.getQueue().clear();
                }
                pool.sort(Comparator.comparingInt(this::queueNoSortKey));
                for (String requestId : pool) {
                    ChargingRequest req = requests.get(requestId);
                    req.setPileId(null);
                    req.setStatus(RequestStatus.REDISPATCHING);
                    redispatchQueue(pile.getMode()).addLast(requestId);
                }
                sortRedispatchQueue(pile.getMode());
            }
            schedule();
            saveAndPublish("PILE_FAULT");
        } finally {
            lock.unlock();
        }
    }

    private int queueNoSortKey(String requestId) {
        ChargingRequest req = requests.get(requestId);
        if (req == null || req.getQueueNo() == null) return Integer.MAX_VALUE;
        String numeric = req.getQueueNo().replaceAll("[^0-9]", "");
        if (numeric.isEmpty()) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(numeric);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    public void recover(String pileId) {
        recover(pileId, FaultStrategy.TIME_ORDER);
    }

    /**
     * Per PDF section 7-c: when a fault pile recovers and OTHER same-mode piles still have
     * vehicles queued (and not yet charging), merge those queued vehicles with the recovered
     * pile and re-dispatch by queueNo order. The currently-charging request on each peer is
     * untouched.
     */
    public void recover(String pileId, FaultStrategy strategy) {
        lock.lock();
        try {
            ChargingPile recovered = pile(pileId);
            recovered.setStatus(PileStatus.WORKING);

            boolean rebalance = strategy == FaultStrategy.TIME_ORDER;
            if (rebalance) {
                rebalanceRecoveredPile(recovered);
            }
            schedule();
            saveAndPublish("PILE_RECOVERED");
        } finally {
            lock.unlock();
        }
    }

    public void stopPile(String pileId) {
        lock.lock();
        try {
            disablePile(pile(pileId));
            schedule();
            saveAndPublish("PILE_DISABLED");
        } finally {
            lock.unlock();
        }
    }

    public void startPile(String pileId) {
        lock.lock();
        try {
            pile(pileId).setStatus(PileStatus.WORKING);
            schedule();
            saveAndPublish("PILE_ENABLED");
        } finally {
            lock.unlock();
        }
    }

    public void stopAllPiles() {
        lock.lock();
        try {
            piles.values().stream()
                    .filter(p -> p.getStatus() != PileStatus.FAULT)
                    .forEach(this::disablePile);
            schedule();
            saveAndPublish("PILES_DISABLED");
        } finally {
            lock.unlock();
        }
    }

    public void startAllPiles() {
        lock.lock();
        try {
            piles.values().forEach(p -> p.setStatus(PileStatus.WORKING));
            schedule();
            saveAndPublish("PILES_ENABLED");
        } finally {
            lock.unlock();
        }
    }

    public SystemSnapshot startStation() {
        lock.lock();
        try {
            stationState = StationState.RUNNING;
            piles.values().stream()
                    .filter(p -> p.getStatus() == PileStatus.DISABLED)
                    .forEach(p -> p.setStatus(PileStatus.WORKING));
            schedule();
            saveAndPublish("STATION_STARTED");
            return snapshotData();
        } finally {
            lock.unlock();
        }
    }

    public SystemSnapshot stopStation() {
        lock.lock();
        try {
            stationState = StationState.STOPPED;
            saveAndPublish("STATION_STOPPED");
            return snapshotData();
        } finally {
            lock.unlock();
        }
    }

    public StationState stationStateValue() {
        lock.lock();
        try {
            return stationState;
        } finally {
            lock.unlock();
        }
    }

    private void ensureStationRunning() {
        if (stationState == StationState.STOPPED) {
            throw new IllegalArgumentException("充电站当前已停止服务，请管理员先启动充电站");
        }
    }

    public void reset(String time) {
        lock.lock();
        try {
            piles.clear();
            for (int i = 1; i <= fastPileCount; i++) {
                String id = "F" + i;
                piles.put(id, new ChargingPile(id, ChargeMode.FAST));
            }
            for (int i = 1; i <= slowPileCount; i++) {
                String id = "T" + i;
                piles.put(id, new ChargingPile(id, ChargeMode.SLOW));
            }
            requests.clear();
            waitingFast.clear();
            waitingSlow.clear();
            redispatchFast.clear();
            redispatchSlow.clear();
            priorityFast.clear();
            prioritySlow.clear();
            details.clear();
            faultEvents.clear();
            now = currentDate.atTime(LocalTime.parse(time));
            fastSeq = 1;
            slowSeq = 1;
            stationState = StationState.RUNNING;
            billingService.resetDetailSeq(1);
            saveAndPublish("RESET");
        } finally {
            lock.unlock();
        }
    }

    public SystemSnapshot updateStationConfig(Integer newWaitingCapacity, Integer newPileSlotCapacity) {
        return updateStationConfig(newWaitingCapacity, newPileSlotCapacity, null, null, null, null);
    }

    public SystemSnapshot updateStationConfig(Integer newWaitingCapacity,
                                              Integer newPileSlotCapacity,
                                              Integer newFastPileCount,
                                              Integer newSlowPileCount,
                                              BigDecimal newParkingRate,
                                              Integer newParkingGraceMinutes) {
        lock.lock();
        try {
            int nextWaitingCapacity = newWaitingCapacity == null ? waitingCapacity : newWaitingCapacity;
            int nextPileSlotCapacity = newPileSlotCapacity == null ? pileSlotCapacity : newPileSlotCapacity;
            validateStationConfig(nextWaitingCapacity, nextPileSlotCapacity);
            waitingCapacity = nextWaitingCapacity;
            pileSlotCapacity = nextPileSlotCapacity;

            if (newFastPileCount != null || newSlowPileCount != null) {
                int nextFast = newFastPileCount == null ? fastPileCount : newFastPileCount;
                int nextSlow = newSlowPileCount == null ? slowPileCount : newSlowPileCount;
                applyPileCountChange(nextFast, nextSlow);
            }
            if (newParkingRate != null) {
                if (newParkingRate.signum() < 0) {
                    throw new IllegalArgumentException("parkingRate must be non-negative");
                }
                parkingRate = newParkingRate;
            }
            if (newParkingGraceMinutes != null) {
                if (newParkingGraceMinutes < 0) {
                    throw new IllegalArgumentException("parkingGracePeriodMinutes must be non-negative");
                }
                parkingGracePeriodMinutes = newParkingGraceMinutes;
            }
            schedule();
            saveAndPublish("CONFIG_UPDATED");
            return snapshotData();
        } finally {
            lock.unlock();
        }
    }

    private void applyPileCountChange(int nextFast, int nextSlow) {
        if (nextFast < 0 || nextFast > MAX_PILE_COUNT_PER_MODE) {
            throw new IllegalArgumentException("fastPileCount must be between 0 and " + MAX_PILE_COUNT_PER_MODE);
        }
        if (nextSlow < 0 || nextSlow > MAX_PILE_COUNT_PER_MODE) {
            throw new IllegalArgumentException("slowPileCount must be between 0 and " + MAX_PILE_COUNT_PER_MODE);
        }
        if (nextFast == fastPileCount && nextSlow == slowPileCount) {
            return;
        }
        boolean queuesEmpty = waitingFast.isEmpty() && waitingSlow.isEmpty()
                && redispatchFast.isEmpty() && redispatchSlow.isEmpty()
                && priorityFast.isEmpty() && prioritySlow.isEmpty();
        boolean pilesIdle = piles.values().stream()
                .allMatch(p -> p.getCurrentRequestId() == null && p.getQueue().isEmpty());
        if (!queuesEmpty || !pilesIdle) {
            throw new IllegalArgumentException("需先清空所有等候/桩内队列再调整桩数");
        }

        Map<String, ChargingPile> rebuilt = new LinkedHashMap<>();
        for (int i = 1; i <= nextFast; i++) {
            String id = "F" + i;
            ChargingPile existing = piles.get(id);
            rebuilt.put(id, existing != null && existing.getMode() == ChargeMode.FAST
                    ? existing
                    : new ChargingPile(id, ChargeMode.FAST));
        }
        for (int i = 1; i <= nextSlow; i++) {
            String id = "T" + i;
            ChargingPile existing = piles.get(id);
            rebuilt.put(id, existing != null && existing.getMode() == ChargeMode.SLOW
                    ? existing
                    : new ChargingPile(id, ChargeMode.SLOW));
        }
        piles.clear();
        piles.putAll(rebuilt);
        fastPileCount = nextFast;
        slowPileCount = nextSlow;
        fastSeq = 1;
        slowSeq = 1;
    }

    public LocalDateTime advanceTo(String time) {
        LocalDateTime target = currentDate.atTime(LocalTime.parse(time));
        if (target.isBefore(now)) {
            throw new IllegalArgumentException("Cannot move clock backwards from " + now + " to " + target);
        }
        return advanceMinutes(java.time.Duration.between(now, target).toMinutes());
    }

    public LocalDateTime advanceMinutes(long minutes) {
        lock.lock();
        try {
            for (long i = 0; i < minutes; i++) {
                now = now.plusMinutes(1);
                recoverDueFaults();
                tickChargingOneMinute();
                schedule();
            }
            saveAndPublish("CLOCK_ADVANCED");
            return now;
        } finally {
            lock.unlock();
        }
    }

    public SystemSnapshot snapshot() {
        lock.lock();
        try {
            return snapshotData();
        } finally {
            lock.unlock();
        }
    }

    /** Manual trigger of the scheduling pass. Equivalent to what every state-changing op already calls. */
    public SystemSnapshot scheduleRequest() {
        lock.lock();
        try {
            schedule();
            saveAndPublish("SCHEDULE_TRIGGERED");
            return snapshotData();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Manual "call number" for a specific pile: pop the first request of that pile's mode
     * from the waiting/priority queue and assign it. If the pile is full or no candidate
     * exists, throws.
     */
    public SystemSnapshot callNumber(String pileId) {
        lock.lock();
        try {
            ChargingPile pile = pile(pileId);
            if (!pile.isWorking()) {
                throw new IllegalArgumentException("Pile " + pileId + " is not working (status=" + pile.getStatus() + ")");
            }
            if (!pile.hasCapacity(pileSlotCapacity)) {
                throw new IllegalArgumentException("Pile " + pileId + " queue is full");
            }
            ChargeMode mode = pile.getMode();
            Deque<String> source = !redispatchQueue(mode).isEmpty()
                    ? redispatchQueue(mode)
                    : (priorityQueue(mode).isEmpty() ? waitingQueue(mode) : priorityQueue(mode));
            if (source.isEmpty()) {
                throw new IllegalArgumentException("Waiting queue for mode " + mode + " is empty");
            }
            String reqId = source.removeFirst();
            ChargingRequest req = requests.get(reqId);
            assignToPile(req, pile);
            saveAndPublish("CALL_NUMBER");
            return snapshotData();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 题目 PDF 第 8-a 条「单次调度总充电时长最短」：等候区出现多个空位时一次同时调度多车。
     * 按充电模式分组（FAST/SLOW），每组用 SPT (Shortest Processing Time) + Load-Balance
     * 启发式（已知对 m-machine total flowtime 最小化在 SRPT 下最优）。
     *
     * 只考虑等候区车辆（含 priority），不打扰已在桩内的车；apply=true 时把车辆按计划入桩。
     */
    public ExtendedSchedulePlanDto scheduleExtendedA(boolean apply) {
        lock.lock();
        try {
            ensureStationRunning();
            // 8-a 语义：等候区 + 桩内排队（非 currentRequest）全部参与重组，按模式分配
            // 用于"管理员一次性重新洗牌"。dry-run 时不破坏现状。
            List<ChargingRequest> snapshotPool = collectReorderableSnapshot();
            List<ChargingRequest> fastReqs = snapshotPool.stream()
                    .filter(r -> r.getMode() == ChargeMode.FAST).toList();
            List<ChargingRequest> slowReqs = snapshotPool.stream()
                    .filter(r -> r.getMode() == ChargeMode.SLOW).toList();
            List<ChargingPile> fastPiles = piles.values().stream()
                    .filter(p -> p.getMode() == ChargeMode.FAST && p.isWorking()).toList();
            List<ChargingPile> slowPiles = piles.values().stream()
                    .filter(p -> p.getMode() == ChargeMode.SLOW && p.isWorking()).toList();

            List<ExtendedSchedulePlanDto.Assignment> all = new ArrayList<>();
            long total = 0;
            // 注意 planSPT 内部会读取 pile.currentRequestId 累加初始负载，所以预览不破坏现状即可
            total += planSPT(new ArrayList<>(fastReqs), fastPiles, all);
            total += planSPT(new ArrayList<>(slowReqs), slowPiles, all);

            if (apply) {
                drainAllReorderableForExtended();   // actually clear queues
                applyExtendedPlan(all);
                saveAndPublish("SCHEDULE_EXTENDED_A");
            }
            return new ExtendedSchedulePlanDto(
                    "MODE_AWARE", total, all.size(),
                    fastPiles.size() + slowPiles.size(), apply, all);
        } finally {
            lock.unlock();
        }
    }

    /** Snapshot all reorderable requests WITHOUT mutating any queues — used for dry-run. */
    private List<ChargingRequest> collectReorderableSnapshot() {
        return requests.values().stream()
                .filter(r -> r.getStatus() == RequestStatus.WAITING_AREA
                        || r.getStatus() == RequestStatus.REDISPATCHING
                        || r.getStatus() == RequestStatus.PILE_QUEUE)
                .toList();
    }

    /**
     * 题目 PDF 第 8-b 条「批量调度总充电时长最短」：当 到站车辆数 == 总车位（充电区+等候区）时
     * 才触发，所有车辆不区分快慢充模式可分配到任意桩；目标是 batch 总时长最短。
     *
     * 校验：需要 totalActiveCars >= (chargingArea + waitingArea) ；否则 400。
     */
    public ExtendedSchedulePlanDto scheduleExtendedB(boolean apply) {
        lock.lock();
        try {
            ensureStationRunning();
            int totalCapacity = piles.size() * pileSlotCapacity + waitingCapacity;
            int totalActive = (int) requests.values().stream()
                    .filter(r -> r.getStatus() == RequestStatus.WAITING_AREA
                            || r.getStatus() == RequestStatus.REDISPATCHING
                            || r.getStatus() == RequestStatus.PILE_QUEUE
                            || r.getStatus() == RequestStatus.CHARGING)
                    .count();
            if (totalActive < totalCapacity) {
                throw new IllegalArgumentException(
                        "批量调度需要在站车辆数 == 总车位（" + totalCapacity + "），当前 "
                                + totalActive + "；请等待车辆到齐或改用扩展调度 A");
            }
            // 8-b 不区分模式：把所有"未在充电"的车统一重排
            List<ChargingRequest> snapshotPool = collectReorderableSnapshot();
            List<ChargingPile> allWorking = piles.values().stream()
                    .filter(ChargingPile::isWorking).toList();

            List<ExtendedSchedulePlanDto.Assignment> all = new ArrayList<>();
            long total = planSPT(new ArrayList<>(snapshotPool), allWorking, all);

            if (apply) {
                drainAllReorderableForExtended();
                applyExtendedPlan(all);
                saveAndPublish("SCHEDULE_EXTENDED_B");
            }
            return new ExtendedSchedulePlanDto(
                    "MODE_FREE", total, all.size(), allWorking.size(), apply, all);
        } finally {
            lock.unlock();
        }
    }

    /** SPT + Load-Balance: 车按充电时间升序，依次分给当前总负载最小的桩。 */
    private long planSPT(List<ChargingRequest> reqs,
                         List<ChargingPile> targetPiles,
                         List<ExtendedSchedulePlanDto.Assignment> out) {
        if (reqs.isEmpty() || targetPiles.isEmpty()) return 0;
        // 按"该车在某桩需要的充电时间"近似（不同模式桩功率不同）—— 用 FAST 桩功率作为代理排序
        // 实际上同组内功率相同（A 调度按模式分组，B 调度功率仍有差异）。
        // 对 8-b 来说，分配到不同功率桩充电时间会变，所以这里直接按"在最快桩的耗时"排序更稳。
        reqs.sort(Comparator.comparingLong(r -> requiredMinutes(r.getRequestedKwh(), maxPower(targetPiles))));

        Map<String, Long> pileLoad = new HashMap<>();
        Map<String, List<ExtendedSchedulePlanDto.Assignment>> pileOrder = new HashMap<>();
        targetPiles.forEach(p -> {
            // 包含 currentRequestId 当前残留负载
            long load = 0;
            if (p.getCurrentRequestId() != null) {
                ChargingRequest c = requests.get(p.getCurrentRequestId());
                if (c != null) load = remainingMinutes(c, p.getPowerKw());
            }
            pileLoad.put(p.getId(), load);
            pileOrder.put(p.getId(), new ArrayList<>());
        });

        long totalCompletion = 0;
        for (ChargingRequest req : reqs) {
            // 选当前 load 最小的桩；平局按桩号字典序（与系统其他调度一致）
            ChargingPile best = targetPiles.stream()
                    .min(Comparator.<ChargingPile>comparingLong(p -> pileLoad.get(p.getId()))
                            .thenComparing(ChargingPile::getId))
                    .orElseThrow();
            long wait = pileLoad.get(best.getId());
            long charge = requiredMinutes(req.getRequestedKwh(), best.getPowerKw());
            long completion = wait + charge;
            totalCompletion += completion;

            int slot = pileOrder.get(best.getId()).size();
            ExtendedSchedulePlanDto.Assignment a = new ExtendedSchedulePlanDto.Assignment(
                    req.getVehicleId(), req.getQueueNo(), req.getMode().name(),
                    best.getId(), slot, wait, charge, completion
            );
            pileOrder.get(best.getId()).add(a);
            out.add(a);
            pileLoad.merge(best.getId(), charge, Long::sum);
        }
        return totalCompletion;
    }

    private int maxPower(List<ChargingPile> ps) {
        return ps.stream().mapToInt(ChargingPile::getPowerKw).max().orElse(30);
    }

    /** Drain waiting queue (waiting + priority) for a given mode and return the requests in order. */
    private List<ChargingRequest> drainWaitingForExtended(ChargeMode mode) {
        List<ChargingRequest> out = new ArrayList<>();
        Deque<String> waiting = waitingQueue(mode);
        Deque<String> redispatch = redispatchQueue(mode);
        Deque<String> priority = priorityQueue(mode);
        redispatch.forEach(id -> { ChargingRequest r = requests.get(id); if (r != null) out.add(r); });
        priority.forEach(id -> { ChargingRequest r = requests.get(id); if (r != null) out.add(r); });
        waiting.forEach(id -> { ChargingRequest r = requests.get(id); if (r != null) out.add(r); });
        waiting.clear();
        redispatch.clear();
        priority.clear();
        return out;
    }

    /** Drain ALL reorderable requests (not currently charging) — for batch 8-b. */
    private List<ChargingRequest> drainAllReorderableForExtended() {
        List<ChargingRequest> out = new ArrayList<>();
        for (ChargingRequest r : requests.values()) {
            if (r.getStatus() == RequestStatus.WAITING_AREA
                    || r.getStatus() == RequestStatus.REDISPATCHING
                    || r.getStatus() == RequestStatus.PILE_QUEUE) {
                out.add(r);
            }
        }
        // also clear queue / pile-internal queues for affected requests
        out.forEach(r -> {
            removeFromAllQueues(r.getId());
            r.setStatus(RequestStatus.WAITING_AREA);
            r.setPileId(null);
        });
        return out;
    }

    /** Apply a planned set of assignments: clear & rebuild pile queues; first slot becomes currentRequest if pile is idle. */
    private void applyExtendedPlan(List<ExtendedSchedulePlanDto.Assignment> plan) {
        // group by pileId, sorted by slotIndex
        Map<String, List<ExtendedSchedulePlanDto.Assignment>> byPile = new LinkedHashMap<>();
        plan.forEach(a -> byPile.computeIfAbsent(a.pileId(), k -> new ArrayList<>()).add(a));
        for (Map.Entry<String, List<ExtendedSchedulePlanDto.Assignment>> e : byPile.entrySet()) {
            ChargingPile pile = piles.get(e.getKey());
            if (pile == null) continue;
            List<ExtendedSchedulePlanDto.Assignment> list = e.getValue();
            list.sort(Comparator.comparingInt(ExtendedSchedulePlanDto.Assignment::slotIndex));
            // Drop pile-queue (not currentRequest)
            pile.getQueue().clear();
            for (ExtendedSchedulePlanDto.Assignment a : list) {
                ChargingRequest r = requests.values().stream()
                        .filter(x -> x.getVehicleId().equals(a.vehicleId())
                                && (x.getStatus() == RequestStatus.WAITING_AREA
                                        || x.getStatus() == RequestStatus.REDISPATCHING
                                        || x.getStatus() == RequestStatus.PILE_QUEUE))
                        .findFirst().orElse(null);
                if (r == null) continue;
                r.setPileId(pile.getId());
                if (pile.getCurrentRequestId() == null) {
                    pile.setCurrentRequestId(r.getId());
                    r.setStatus(RequestStatus.CHARGING);
                    r.setStartedAt(now);
                } else {
                    pile.getQueue().add(r.getId());
                    r.setStatus(RequestStatus.PILE_QUEUE);
                }
            }
        }
    }

    public List<ChargingDetail> detailsFor(String vehicleId) {
        lock.lock();
        try {
            return details.stream().filter(d -> d.getVehicleId().equals(vehicleId)).toList();
        } finally {
            lock.unlock();
        }
    }

    public List<ChargingDetail> allDetails() {
        lock.lock();
        try {
            return new ArrayList<>(details);
        } finally {
            lock.unlock();
        }
    }

    public List<QueueDetailDto> queueDetailsForPile(String pileId) {
        lock.lock();
        try {
            ChargingPile pile = pile(pileId);
            List<QueueDetailDto> list = new ArrayList<>();
            for (String requestId : pile.getQueue()) {
                ChargingRequest req = requests.get(requestId);
                if (req != null) list.add(toQueueDetail(req));
            }
            return list;
        } finally {
            lock.unlock();
        }
    }

    public List<QueueDetailDto> waitingAreaDetails(ChargeMode mode) {
        lock.lock();
        try {
            List<QueueDetailDto> list = new ArrayList<>();
            for (String requestId : waitingQueue(mode)) {
                ChargingRequest req = requests.get(requestId);
                if (req != null) list.add(toQueueDetail(req));
            }
            return list;
        } finally {
            lock.unlock();
        }
    }

    private QueueDetailDto toQueueDetail(ChargingRequest req) {
        long wait = req.getCreatedAt() == null ? 0 : Math.max(0, java.time.Duration.between(req.getCreatedAt(), now).toMinutes());
        return new QueueDetailDto(
                req.getVehicleId(),
                req.getVehicleId(),
                req.getMode(),
                req.getQueueNo(),
                req.getBatteryCapacity(),
                req.getRequestedKwh(),
                wait,
                null,
                req.getStatus(),
                req.getPileId()
        );
    }

    public List<BillDto> billsFor(String vehicleId) {
        lock.lock();
        try {
            return billsForVehicle(vehicleId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Aggregate all charging details for a vehicle within `scope` ({@code DAY|WEEK|MONTH|ALL})
     * into a single bill object, as required by 概要设计 v3 UC_08 `Create_Bill`.
     */
    public BillAggregateDto billAggregate(String vehicleId, String scope) {
        lock.lock();
        try {
            List<BillDto> matched = billsForVehicle(vehicleId).stream()
                    .filter(b -> billInScope(b, scope))
                    .toList();
            BigDecimal totalKwh = matched.stream().map(BillDto::chargedKwh).reduce(BigDecimal.ZERO, BigDecimal::add);
            long totalDur = matched.stream().mapToLong(BillDto::durationMinutes).sum();
            BigDecimal totalCharge = matched.stream().map(BillDto::chargeFee).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalService = matched.stream().map(BillDto::serviceFee).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalFee = matched.stream().map(BillDto::totalFee).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal unpaid = matched.stream().filter(b -> !b.paid()).map(BillDto::totalFee).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean allPaid = matched.stream().allMatch(BillDto::paid);
            LocalDateTime periodStart = matched.stream().map(BillDto::startedAt).filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null);
            LocalDateTime periodEnd = matched.stream().map(BillDto::stoppedAt).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
            String normalized = scope == null ? "ALL" : scope.trim().toUpperCase(Locale.ROOT);
            String billId = String.format("BILL-%s-%s-%s",
                    vehicleId,
                    normalized,
                    matched.isEmpty() ? "EMPTY" : matched.get(matched.size() - 1).detailNo());
            return new BillAggregateDto(
                    billId,
                    vehicleId,
                    normalized,
                    periodStart,
                    periodEnd,
                    matched.size(),
                    totalKwh,
                    totalDur,
                    totalCharge,
                    totalService,
                    totalFee,
                    allPaid && !matched.isEmpty(),
                    unpaid,
                    matched
            );
        } finally {
            lock.unlock();
        }
    }

    private boolean billInScope(BillDto b, String scope) {
        if (b.startedAt() == null) return true;
        String normalized = scope == null ? "ALL" : scope.trim().toUpperCase(Locale.ROOT);
        java.time.LocalDate date = b.startedAt().toLocalDate();
        java.time.LocalDate today = now.toLocalDate();
        return switch (normalized) {
            case "DAY" -> date.equals(today);
            case "WEEK" -> {
                java.time.LocalDate start = today.minusDays(today.getDayOfWeek().getValue() - java.time.DayOfWeek.MONDAY.getValue());
                java.time.LocalDate end = start.plusDays(6);
                yield !date.isBefore(start) && !date.isAfter(end);
            }
            case "MONTH" -> date.getYear() == today.getYear() && date.getMonth() == today.getMonth();
            default -> true;
        };
    }

    public VehicleStatusDto vehicleStatus(String vehicleId) {
        lock.lock();
        try {
            List<RequestSnapshot> vehicleRequests = requestSnapshots().stream()
                    .filter(r -> r.vehicleId().equals(vehicleId))
                    .toList();
            RequestSnapshot active = vehicleRequests.stream()
                    .filter(r -> r.status() != RequestStatus.COMPLETED
                            && r.status() != RequestStatus.CANCELLED
                            && r.status() != RequestStatus.PAID)
                    .reduce((first, second) -> second)
                    .orElse(null);
            String message = active == null ? "No active charging request" : statusMessage(active);
            return new VehicleStatusDto(vehicleId, active, vehicleRequests, billsForVehicle(vehicleId), queueAhead(active), message);
        } finally {
            lock.unlock();
        }
    }

    public AdminReportDto adminReport() {
        return adminReport("ALL", null, null, null);
    }

    public AdminReportDto adminReport(String scope, String pileId, String mode, String vehicleId) {
        lock.lock();
        try {
            ChargeMode modeFilter = parseModeFilter(mode);
            List<ChargingDetail> filteredDetails = filteredDetails(scope, pileId, modeFilter, vehicleId);
            List<RequestSnapshot> requestList = requestSnapshots().stream()
                    .filter(r -> matchesText(r.vehicleId(), vehicleId))
                    .filter(r -> pileId == null || pileId.isBlank() || pileId.equalsIgnoreCase(String.valueOf(r.pileId())))
                    .filter(r -> modeFilter == null || r.mode() == modeFilter)
                    .toList();
            int activeCount = (int) requestList.stream().filter(r -> r.status() != RequestStatus.COMPLETED
                    && r.status() != RequestStatus.CANCELLED
                    && r.status() != RequestStatus.PAID).count();
            int chargingCount = (int) requestList.stream().filter(r -> r.status() == RequestStatus.CHARGING).count();
            int pileQueueCount = (int) requestList.stream().filter(r -> r.status() == RequestStatus.PILE_QUEUE).count();
            int completedCount = (int) requestList.stream().filter(r -> r.status() == RequestStatus.COMPLETED).count();
            int paidCount = (int) requestList.stream().filter(r -> r.status() == RequestStatus.PAID).count();
            int faultPileCount = (int) piles.values().stream().filter(p -> p.getStatus() == PileStatus.FAULT).count();
            long totalDurationMinutes = filteredDetails.stream().mapToLong(ChargingDetail::getDurationMinutes).sum();
            BigDecimal totalKwh = filteredDetails.stream().map(ChargingDetail::getChargedKwh).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal chargeFee = filteredDetails.stream().map(ChargingDetail::getChargeFee).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal serviceFee = filteredDetails.stream().map(ChargingDetail::getServiceFee).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalFee = filteredDetails.stream().map(ChargingDetail::getTotalFee).reduce(BigDecimal.ZERO, BigDecimal::add);

            return new AdminReportDto(
                    now,
                    requestList.size(),
                    activeCount,
                    (int) requestList.stream().filter(r -> r.status() == RequestStatus.WAITING_AREA).count(),
                    chargingCount,
                    pileQueueCount,
                    completedCount,
                    paidCount,
                    faultPileCount,
                    filteredDetails.size(),
                    totalDurationMinutes,
                    totalKwh,
                    chargeFee,
                    serviceFee,
                    totalFee,
                    pileReports(filteredDetails, modeFilter, pileId)
            );
        } finally {
            lock.unlock();
        }
    }

    public List<ChargingDetail> reportDetails(String scope, String pileId, String mode, String vehicleId) {
        lock.lock();
        try {
            return filteredDetails(scope, pileId, parseModeFilter(mode), vehicleId);
        } finally {
            lock.unlock();
        }
    }

    public void applyAcceptanceEvent(String action, String subject, String mode, BigDecimal amount) {
        applyAcceptanceEvent(action, subject, mode, amount, FaultStrategy.PRIORITY);
    }

    public void applyAcceptanceEvent(String action, String subject, String mode, BigDecimal amount, FaultStrategy faultStrategy) {
        switch (action.toUpperCase(Locale.ROOT)) {
            case "A" -> {
                if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) cancel(subject);
                else submit(subject, ChargeMode.fromCode(mode), amount);
            }
            case "C" -> applyAcceptanceChange(subject, mode, amount);
            case "B" -> reportFault(subject, amount == null ? 0 : amount.longValue(),
                    faultStrategy == null ? FaultStrategy.PRIORITY : faultStrategy);
            default -> throw new IllegalArgumentException("Unsupported acceptance action: " + action);
        }
    }

    /**
     * 验收 Excel 的 C 事件语义："变更充电请求"。按需求 PDF 第 6 条，HTTP 路径上的 change()
     * 在充电中应严格拒绝；但验收 Excel 中确有 V19/V21/V22 在 CHARGING 中变更的事件。
     * 这里在验收处理层等价实现"取消 + 重新申请"的语义：
     *   - 已充部分作为 1 张详单结算（与设计 7-c "故障/中断车辆产生详单"一致）；
     *   - 剩余电量作为新请求进入等候区（与设计 6-a-2 "取消重新进入等候区排队"一致）。
     */
    private void applyAcceptanceChange(String vehicleId, String modeStr, BigDecimal amount) {
        lock.lock();
        try {
            ChargeMode targetMode = ChargeMode.fromCode(modeStr);
            ChargingRequest current;
            try {
                current = activeRequest(vehicleId);
            } catch (IllegalArgumentException notFound) {
                submit(vehicleId, targetMode, amount);
                return;
            }
            if (current.getStatus() != RequestStatus.CHARGING
                    && current.getStatus() != RequestStatus.PILE_QUEUE) {
                change(vehicleId, targetMode, amount);
                return;
            }
            // CHARGING / PILE_QUEUE 分支：结算已充部分 + 剩余电量新建请求
            String currentPileId = current.getPileId();
            if (current.getMode() == targetMode) {
                if (amount.compareTo(current.getChargedKwh()) <= 0) {
                    current.setRequestedKwh(current.getChargedKwh());
                    finishCharging(current, RequestStatus.COMPLETED);
                    startNextIfPossible(currentPileId);
                    schedule();
                } else {
                    current.setRequestedKwh(amount);
                }
                saveAndPublish("REQUEST_CHANGED");
                return;
            }
            finishCharging(current, RequestStatus.COMPLETED);
            startNextIfPossible(currentPileId);
            BigDecimal remainingKwh = amount.subtract(current.getChargedKwh()).max(BigDecimal.ZERO);
            if (remainingKwh.compareTo(BigDecimal.ZERO) == 0) {
                schedule();
                saveAndPublish("REQUEST_CHANGED");
                return;
            }
            ChargingRequest replacement = new ChargingRequest(
                    current.getVehicleId(), targetMode, remainingKwh, nextQueueNo(targetMode), now);
            requests.put(replacement.getId(), replacement);
            waitingQueue(targetMode).addLast(replacement.getId());
            schedule();
            saveAndPublish("REQUEST_CHANGED");
        } finally {
            lock.unlock();
        }
    }

    private void schedule() {
        drainRedispatch(ChargeMode.FAST);
        drainRedispatch(ChargeMode.SLOW);
        if (redispatchFast.isEmpty() && redispatchSlow.isEmpty()) {
            drainPriority(ChargeMode.FAST);
            drainPriority(ChargeMode.SLOW);
        }
        if (redispatchFast.isEmpty() && redispatchSlow.isEmpty()
                && priorityFast.isEmpty() && prioritySlow.isEmpty()) {
            drainWaiting(ChargeMode.FAST);
            drainWaiting(ChargeMode.SLOW);
        }
    }

    private void drainRedispatch(ChargeMode mode) {
        drainQueue(redispatchQueue(mode), mode);
    }

    private void drainPriority(ChargeMode mode) {
        drainQueue(priorityQueue(mode), mode);
    }

    private void drainWaiting(ChargeMode mode) {
        drainQueue(waitingQueue(mode), mode);
    }

    private void drainQueue(Deque<String> queue, ChargeMode mode) {
        boolean moved;
        do {
            moved = false;
            if (queue.isEmpty()) return;
            ChargingPile target = bestPile(mode, requests.get(queue.peekFirst()));
            if (target != null) {
                String requestId = queue.removeFirst();
                assignToPile(requests.get(requestId), target);
                moved = true;
            }
        } while (moved);
    }

    private ChargingPile bestPile(ChargeMode mode, ChargingRequest request) {
        return piles.values().stream()
                .filter(p -> p.getMode() == mode)
                .filter(p -> p.hasCapacity(pileSlotCapacity))
                .min(Comparator.comparingLong((ChargingPile p) -> estimatedMinutes(p, request))
                        .thenComparing(ChargingPile::getId))
                .orElse(null);
    }

    private long estimatedMinutes(ChargingPile pile, ChargingRequest newRequest) {
        long minutes = 0;
        if (pile.getCurrentRequestId() != null) {
            ChargingRequest current = requests.get(pile.getCurrentRequestId());
            minutes += remainingMinutes(current, pile.getPowerKw());
        }
        for (String requestId : pile.getQueue()) {
            minutes += requiredMinutes(requests.get(requestId).getRequestedKwh(), pile.getPowerKw());
        }
        minutes += requiredMinutes(newRequest.getRequestedKwh(), pile.getPowerKw());
        return minutes;
    }

    private void assignToPile(ChargingRequest request, ChargingPile pile) {
        request.setPileId(pile.getId());
        if (pile.getCurrentRequestId() == null) {
            pile.setCurrentRequestId(request.getId());
            request.setStatus(RequestStatus.CHARGING);
            request.setStartedAt(now);
        } else {
            pile.getQueue().add(request.getId());
            request.setStatus(RequestStatus.PILE_QUEUE);
        }
    }

    private void tickChargingOneMinute() {
        for (ChargingPile pile : piles.values()) {
            if (pile.getStatus() == PileStatus.FAULT || pile.getCurrentRequestId() == null) continue;
            ChargingRequest request = requests.get(pile.getCurrentRequestId());
            BigDecimal perMinute = BigDecimal.valueOf(pile.getPowerKw()).divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP);
            BigDecimal remaining = request.getRequestedKwh().subtract(request.getChargedKwh());
            BigDecimal delta = perMinute.min(remaining);
            request.setChargedKwh(request.getChargedKwh().add(delta));
            if (request.getChargedKwh().compareTo(request.getRequestedKwh()) >= 0) {
                finishCharging(request, RequestStatus.COMPLETED);
                pile.setCurrentRequestId(null);
                startNextIfPossible(pile.getId());
            }
        }
    }

    private void finishCharging(ChargingRequest request, RequestStatus status) {
        if (request.getStartedAt() != null && request.getStoppedAt() == null) {
            request.setStoppedAt(now);
            if (request.getChargedKwh().compareTo(BigDecimal.ZERO) > 0) {
                details.add(billingService.createDetail(request, request.getPileId(), now, status));
            }
        }
        request.setStatus(status);
        ChargingPile pile = piles.get(request.getPileId());
        if (pile != null && request.getId().equals(pile.getCurrentRequestId())) {
            pile.setCurrentRequestId(null);
        }
    }

    private String interruptCurrentForRedispatch(ChargingPile pile) {
        String requestId = pile.getCurrentRequestId();
        if (requestId == null) return null;
        ChargingRequest current = requests.get(requestId);
        BigDecimal remainingKwh = current.getRequestedKwh().subtract(current.getChargedKwh()).max(BigDecimal.ZERO);
        finishCharging(current, remainingKwh.compareTo(BigDecimal.ZERO) > 0 ? RequestStatus.INTERRUPTED : RequestStatus.COMPLETED);
        pile.setCurrentRequestId(null);
        if (remainingKwh.compareTo(BigDecimal.ZERO) <= 0) return null;
        current.setRequestedKwh(remainingKwh);
        current.setChargedKwh(BigDecimal.ZERO);
        current.setStartedAt(null);
        current.setStoppedAt(null);
        current.setPileId(null);
        current.setStatus(RequestStatus.REDISPATCHING);
        return requestId;
    }

    private void startNextIfPossible(String pileId) {
        ChargingPile pile = piles.get(pileId);
        if (pile == null || !pile.isWorking() || pile.getCurrentRequestId() != null || pile.getQueue().isEmpty()) return;
        String nextId = pile.getQueue().remove(0);
        ChargingRequest next = requests.get(nextId);
        pile.setCurrentRequestId(nextId);
        next.setStatus(RequestStatus.CHARGING);
        next.setStartedAt(now);
    }

    private void recoverDueFaults() {
        for (ChargingPile pile : piles.values()) {
            if (pile.getStatus() != PileStatus.FAULT) continue;
            boolean due = faultEvents.stream()
                    .filter(e -> e.getPileId().equals(pile.getId()))
                    .anyMatch(e -> !e.getRecoverAt().isAfter(now));
            if (due) {
                pile.setStatus(PileStatus.WORKING);
                rebalanceRecoveredPile(pile);
            }
        }
    }

    private void rebalanceRecoveredPile(ChargingPile recovered) {
        List<ChargingPile> peers = piles.values().stream()
                .filter(p -> p.getMode() == recovered.getMode()
                        && !p.getId().equals(recovered.getId())
                        && p.getStatus() == PileStatus.WORKING)
                .toList();
        boolean peerHasQueued = peers.stream().anyMatch(p -> !p.getQueue().isEmpty());
        if (!peerHasQueued) {
            return;
        }
        List<String> pool = new ArrayList<>();
        for (ChargingPile peer : peers) {
            pool.addAll(peer.getQueue());
            peer.getQueue().clear();
        }
        pool.sort(Comparator.comparingInt(this::queueNoSortKey));
        for (String requestId : pool) {
            ChargingRequest req = requests.get(requestId);
            req.setPileId(null);
            req.setStatus(RequestStatus.REDISPATCHING);
            redispatchQueue(recovered.getMode()).addLast(requestId);
        }
        sortRedispatchQueue(recovered.getMode());
    }

    private void sortRedispatchQueue(ChargeMode mode) {
        Deque<String> queue = redispatchQueue(mode);
        List<String> sorted = new ArrayList<>(queue);
        sorted.sort(Comparator.comparingInt(this::queueNoSortKey));
        queue.clear();
        queue.addAll(sorted);
    }

    private long requiredMinutes(BigDecimal kwh, int powerKw) {
        return kwh.multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(powerKw), 0, RoundingMode.CEILING).longValue();
    }

    private long remainingMinutes(ChargingRequest request, int powerKw) {
        return requiredMinutes(request.getRequestedKwh().subtract(request.getChargedKwh()).max(BigDecimal.ZERO), powerKw);
    }

    private void ensureWaitingCapacity() {
        if (waitingFast.size() + waitingSlow.size() >= waitingCapacity) {
            throw new IllegalArgumentException("Waiting area is full");
        }
    }

    private void validateStationConfig(int nextWaitingCapacity, int nextPileSlotCapacity) {
        if (nextWaitingCapacity < 0 || nextWaitingCapacity > 99) {
            throw new IllegalArgumentException("Waiting capacity N must be between 0 and 99");
        }
        if (nextPileSlotCapacity < 1 || nextPileSlotCapacity > 8) {
            throw new IllegalArgumentException("Pile slot capacity M must be between 1 and 8");
        }
        int waitingOccupancy = waitingFast.size() + waitingSlow.size();
        if (nextWaitingCapacity < waitingOccupancy) {
            throw new IllegalArgumentException("Waiting capacity N cannot be less than current waiting vehicles: " + waitingOccupancy);
        }
        int maxPileOccupancy = piles.values().stream()
                .mapToInt(ChargingPile::occupiedSlots)
                .max()
                .orElse(0);
        if (nextPileSlotCapacity < maxPileOccupancy) {
            throw new IllegalArgumentException("Pile slot capacity M cannot be less than current pile occupancy: " + maxPileOccupancy);
        }
    }

    private void ensureNoActiveRequest(String vehicleId) {
        boolean exists = requests.values().stream()
                .filter(r -> r.getVehicleId().equals(vehicleId))
                .anyMatch(r -> r.getStatus() != RequestStatus.COMPLETED
                        && r.getStatus() != RequestStatus.CANCELLED
                        && r.getStatus() != RequestStatus.PAID);
        if (exists) {
            throw new IllegalArgumentException("Vehicle already has an active request: " + vehicleId);
        }
    }

    private String nextQueueNo(ChargeMode mode) {
        return mode.prefix() + (mode == ChargeMode.FAST ? fastSeq++ : slowSeq++);
    }

    private ChargingRequest activeRequest(String vehicleId) {
        return requests.values().stream()
                .filter(r -> r.getVehicleId().equals(vehicleId))
                .filter(r -> r.getStatus() != RequestStatus.COMPLETED && r.getStatus() != RequestStatus.CANCELLED && r.getStatus() != RequestStatus.PAID)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalArgumentException("No active request for vehicle: " + vehicleId));
    }

    private void removeFromAllQueues(String requestId) {
        waitingFast.remove(requestId);
        waitingSlow.remove(requestId);
        redispatchFast.remove(requestId);
        redispatchSlow.remove(requestId);
        priorityFast.remove(requestId);
        prioritySlow.remove(requestId);
        for (ChargingPile pile : piles.values()) {
            pile.getQueue().remove(requestId);
        }
    }

    private ChargingPile pile(String pileId) {
        ChargingPile pile = piles.get(pileId);
        if (pile == null) throw new IllegalArgumentException("Unknown pile: " + pileId);
        return pile;
    }

    private Deque<String> waitingQueue(ChargeMode mode) {
        return mode == ChargeMode.FAST ? waitingFast : waitingSlow;
    }

    private Deque<String> redispatchQueue(ChargeMode mode) {
        return mode == ChargeMode.FAST ? redispatchFast : redispatchSlow;
    }

    private Deque<String> priorityQueue(ChargeMode mode) {
        return mode == ChargeMode.FAST ? priorityFast : prioritySlow;
    }

    private void disablePile(ChargingPile pile) {
        if (pile.getStatus() == PileStatus.FAULT) {
            throw new IllegalArgumentException("Fault pile must recover before stop: " + pile.getId());
        }
        pile.setStatus(PileStatus.DISABLED);
        Deque<String> priority = priorityQueue(pile.getMode());
        for (String queuedId : new ArrayList<>(pile.getQueue())) {
            ChargingRequest queued = requests.get(queuedId);
            queued.setPileId(null);
            queued.setStatus(RequestStatus.REDISPATCHING);
            priority.addLast(queuedId);
        }
        pile.getQueue().clear();
    }

    private List<String> queueVehicles(Deque<String> queue) {
        return queue.stream().map(id -> requests.get(id).getVehicleId()).toList();
    }

    private List<PileSnapshot> pileSnapshots() {
        return piles.values().stream().map(p -> new PileSnapshot(
                p.getId(),
                p.getMode(),
                p.getPowerKw(),
                p.getStatus(),
                p.getCurrentRequestId() == null ? null : requests.get(p.getCurrentRequestId()).getVehicleId(),
                p.getQueue().stream().map(id -> requests.get(id).getVehicleId()).toList()
        )).toList();
    }

    private List<RequestSnapshot> requestSnapshots() {
        return requests.values().stream().map(r -> new RequestSnapshot(
                r.getId(),
                r.getVehicleId(),
                r.getMode(),
                r.getRequestedKwh(),
                r.getQueueNo(),
                r.getStatus(),
                r.getPileId(),
                r.getCreatedAt(),
                r.getStartedAt(),
                r.getStoppedAt(),
                r.getChargedKwh()
        )).toList();
    }

    private SystemSnapshot snapshotData() {
        return new SystemSnapshot(now, stationConfig(), stationState, queueVehicles(waitingFast), queueVehicles(waitingSlow), pileSnapshots(), requestSnapshots(), new ArrayList<>(details));
    }

    public StationConfigDto stationConfig() {
        return new StationConfigDto(
                waitingCapacity,
                pileSlotCapacity,
                fastPileCount,
                slowPileCount,
                FAST_POWER_KW,
                SLOW_POWER_KW,
                parkingRate,
                parkingGracePeriodMinutes
        );
    }

    public LocalDateTime currentTime() {
        lock.lock();
        try {
            return now;
        } finally {
            lock.unlock();
        }
    }

    public BigDecimal parkingRate() {
        return parkingRate;
    }

    public int parkingGracePeriodMinutes() {
        return parkingGracePeriodMinutes;
    }

    private List<BillDto> billsForVehicle(String vehicleId) {
        return details.stream()
                .filter(d -> d.getVehicleId().equals(vehicleId))
                .map(this::toBill)
                .toList();
    }

    private BillDto toBill(ChargingDetail detail) {
        ChargingRequest request = requests.get(detail.getRequestId());
        boolean paid = request != null && request.getStatus() == RequestStatus.PAID;
        return new BillDto(
                detail.getDetailNo(),
                detail.getVehicleId(),
                detail.getRequestId(),
                detail.getPileId(),
                detail.getChargedKwh(),
                detail.getDurationMinutes(),
                detail.getStartedAt(),
                detail.getStoppedAt(),
                detail.getChargeFee(),
                detail.getServiceFee(),
                detail.getTotalFee(),
                detail.getPriceBreakdown(),
                paid
        );
    }

    private String statusMessage(RequestSnapshot request) {
        return switch (request.status()) {
            case WAITING_AREA -> "Waiting for call number";
            case REDISPATCHING -> "Waiting for fault redispatch";
            case PILE_QUEUE -> "Waiting in pile queue " + request.pileId();
            case CHARGING -> "Charging at pile " + request.pileId();
            case INTERRUPTED -> "Interrupted by pile fault";
            case COMPLETED -> "Charging completed";
            case CANCELLED -> "Charging request cancelled";
            case PAID -> "Bill paid";
        };
    }

    private long queueAhead(RequestSnapshot request) {
        if (request == null) return 0;
        if (request.status() == RequestStatus.WAITING_AREA) {
            Deque<String> queue = request.mode() == ChargeMode.FAST ? waitingFast : waitingSlow;
            long ahead = 0;
            for (String requestId : queue) {
                if (requestId.equals(request.id())) return ahead;
                ahead++;
            }
        }
        if (request.status() == RequestStatus.REDISPATCHING) {
            Deque<String> queue = request.mode() == ChargeMode.FAST ? redispatchFast : redispatchSlow;
            long ahead = 0;
            for (String requestId : queue) {
                if (requestId.equals(request.id())) return ahead;
                ahead++;
            }
            queue = request.mode() == ChargeMode.FAST ? priorityFast : prioritySlow;
            for (String requestId : queue) {
                if (requestId.equals(request.id())) return ahead;
                ahead++;
            }
        }
        if (request.status() == RequestStatus.PILE_QUEUE && request.pileId() != null) {
            ChargingPile pile = piles.get(request.pileId());
            if (pile == null) return 0;
            long ahead = pile.getCurrentRequestId() == null ? 0 : 1;
            for (String requestId : pile.getQueue()) {
                if (requestId.equals(request.id())) return ahead;
                ahead++;
            }
        }
        return 0;
    }

    private List<PileReportDto> pileReports(List<ChargingDetail> sourceDetails, ChargeMode modeFilter, String pileIdFilter) {
        return piles.values().stream()
                .filter(pile -> modeFilter == null || pile.getMode() == modeFilter)
                .filter(pile -> pileIdFilter == null || pileIdFilter.isBlank() || pile.getId().equalsIgnoreCase(pileIdFilter))
                .map(pile -> {
            List<ChargingDetail> pileDetails = sourceDetails.stream().filter(d -> d.getPileId().equals(pile.getId())).toList();
            long durationMinutes = pileDetails.stream().mapToLong(ChargingDetail::getDurationMinutes).sum();
            BigDecimal chargedKwh = pileDetails.stream().map(ChargingDetail::getChargedKwh).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal chargeFee = pileDetails.stream().map(ChargingDetail::getChargeFee).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal serviceFee = pileDetails.stream().map(ChargingDetail::getServiceFee).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalFee = pileDetails.stream().map(ChargingDetail::getTotalFee).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new PileReportDto(
                    pile.getId(),
                    pile.getMode(),
                    pile.getStatus(),
                    pile.getCurrentRequestId() == null ? null : requests.get(pile.getCurrentRequestId()).getVehicleId(),
                    pile.getQueue().size(),
                    pileDetails.size(),
                    durationMinutes,
                    chargedKwh,
                    chargeFee,
                    serviceFee,
                    totalFee
            );
        }).toList();
    }

    private List<ChargingDetail> filteredDetails(String scope, String pileId, ChargeMode mode, String vehicleId) {
        return details.stream()
                .filter(d -> inScope(d, scope))
                .filter(d -> pileId == null || pileId.isBlank() || d.getPileId().equalsIgnoreCase(pileId))
                .filter(d -> matchesText(d.getVehicleId(), vehicleId))
                .filter(d -> mode == null || pile(d.getPileId()).getMode() == mode)
                .toList();
    }

    private boolean inScope(ChargingDetail detail, String scope) {
        if (detail.getGeneratedAt() == null) return true;
        String normalized = scope == null ? "ALL" : scope.trim().toUpperCase(Locale.ROOT);
        LocalDate date = detail.getGeneratedAt().toLocalDate();
        LocalDate today = now.toLocalDate();
        return switch (normalized) {
            case "DAY" -> date.equals(today);
            case "WEEK" -> {
                LocalDate start = today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
                LocalDate end = start.plusDays(6);
                yield !date.isBefore(start) && !date.isAfter(end);
            }
            case "MONTH" -> date.getYear() == today.getYear() && date.getMonth() == today.getMonth();
            default -> true;
        };
    }

    private boolean matchesText(String value, String filter) {
        return filter == null || filter.isBlank() || String.valueOf(value).toUpperCase(Locale.ROOT).contains(filter.trim().toUpperCase(Locale.ROOT));
    }

    private ChargeMode parseModeFilter(String mode) {
        if (mode == null || mode.isBlank() || "ALL".equalsIgnoreCase(mode)) return null;
        return ChargeMode.fromCode(mode);
    }

    private boolean restoreState() {
        if (stateStore == null) return false;
        Optional<StationStateData> state = stateStore.load();
        if (state.isEmpty()) return false;

        StationStateData data = state.get();
        currentDate = data.currentDate();
        now = data.now();
        fastSeq = data.fastSeq();
        slowSeq = data.slowSeq();
        waitingCapacity = data.waitingCapacity() <= 0 ? DEFAULT_WAITING_CAPACITY : data.waitingCapacity();
        pileSlotCapacity = data.pileSlotCapacity() <= 0 ? DEFAULT_PILE_SLOT_CAPACITY : data.pileSlotCapacity();
        fastPileCount = data.fastPileCount() > 0 ? data.fastPileCount() : DEFAULT_FAST_PILE_COUNT;
        slowPileCount = data.slowPileCount() > 0 ? data.slowPileCount() : DEFAULT_SLOW_PILE_COUNT;
        parkingRate = data.parkingRate() != null ? data.parkingRate() : DEFAULT_PARKING_RATE;
        parkingGracePeriodMinutes = data.parkingGracePeriodMinutes() >= 0
                ? data.parkingGracePeriodMinutes() : DEFAULT_PARKING_GRACE_MINUTES;
        stationState = data.stationState() != null ? data.stationState() : StationState.RUNNING;
        billingService.resetDetailSeq(data.detailSeq());

        piles.clear();
        piles.putAll(data.piles());
        requests.clear();
        requests.putAll(data.requests());
        waitingFast.clear();
        waitingFast.addAll(data.waitingFast());
        waitingSlow.clear();
        waitingSlow.addAll(data.waitingSlow());
        redispatchFast.clear();
        redispatchFast.addAll(data.redispatchFast());
        redispatchSlow.clear();
        redispatchSlow.addAll(data.redispatchSlow());
        priorityFast.clear();
        priorityFast.addAll(data.priorityFast());
        prioritySlow.clear();
        prioritySlow.addAll(data.prioritySlow());
        details.clear();
        details.addAll(data.details());
        faultEvents.clear();
        faultEvents.addAll(data.faultEvents());
        return true;
    }

    private void saveState() {
        if (stateStore == null) return;
        stateStore.save(new StationStateData(
                currentDate,
                now,
                fastSeq,
                slowSeq,
                nextDetailSeq(),
                waitingCapacity,
                pileSlotCapacity,
                fastPileCount,
                slowPileCount,
                parkingRate,
                parkingGracePeriodMinutes,
                stationState,
                new LinkedHashMap<>(piles),
                new LinkedHashMap<>(requests),
                new ArrayDeque<>(waitingFast),
                new ArrayDeque<>(waitingSlow),
                new ArrayDeque<>(redispatchFast),
                new ArrayDeque<>(redispatchSlow),
                new ArrayDeque<>(priorityFast),
                new ArrayDeque<>(prioritySlow),
                new ArrayList<>(details),
                new ArrayList<>(faultEvents)
        ));
    }

    private void saveAndPublish(String type) {
        saveState();
        if (eventPublisher != null) {
            eventPublisher.publish(type, snapshotData());
        }
    }

    private int nextDetailSeq() {
        return details.stream()
                .map(ChargingDetail::getDetailNo)
                .filter(Objects::nonNull)
                .mapToInt(this::detailSeq)
                .max()
                .orElse(0) + 1;
    }

    private int detailSeq(String detailNo) {
        if (detailNo.length() < 5) return 0;
        try {
            return Integer.parseInt(detailNo.substring(detailNo.length() - 5));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
