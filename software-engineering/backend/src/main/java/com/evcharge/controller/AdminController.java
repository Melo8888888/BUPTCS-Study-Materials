package com.evcharge.controller;

import com.evcharge.domain.ChargingDetail;
import com.evcharge.domain.FaultStrategy;
import com.evcharge.dto.ApiResponse;
import com.evcharge.dto.AdminReportDto;
import com.evcharge.dto.StationConfigDto;
import com.evcharge.dto.SystemSnapshot;
import com.evcharge.dto.UpdateStationConfigRequest;
import com.evcharge.domain.ChargeMode;
import com.evcharge.dto.ExtendedSchedulePlanDto;
import com.evcharge.dto.ParkingFeeDto;
import com.evcharge.dto.QueueDetailDto;
import com.evcharge.persistence.PersistenceReportService;
import com.evcharge.service.ParkingFeeService;
import com.evcharge.service.StationService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final StationService stationService;
    private final PersistenceReportService persistenceReportService;
    private final ParkingFeeService parkingFeeService;

    public AdminController(StationService stationService, PersistenceReportService persistenceReportService,
                           ParkingFeeService parkingFeeService) {
        this.stationService = stationService;
        this.persistenceReportService = persistenceReportService;
        this.parkingFeeService = parkingFeeService;
    }

    @GetMapping("/snapshot")
    public ApiResponse<SystemSnapshot> snapshot() {
        return ApiResponse.ok(stationService.snapshot());
    }

    @GetMapping("/config")
    public ApiResponse<StationConfigDto> config() {
        return ApiResponse.ok(stationService.stationConfig());
    }

    @PostMapping("/config")
    public ApiResponse<SystemSnapshot> updateConfig(@RequestBody UpdateStationConfigRequest request) {
        return ApiResponse.ok(stationService.updateStationConfig(
                request.waitingCapacity(),
                request.pileSlotCapacity(),
                request.fastPileCount(),
                request.slowPileCount(),
                request.parkingRate(),
                request.parkingGracePeriodMinutes()
        ));
    }

    @PostMapping("/clock/reset")
    public ApiResponse<SystemSnapshot> reset(@RequestParam(defaultValue = "06:00") String time) {
        stationService.reset(time);
        return ApiResponse.ok(stationService.snapshot());
    }

    @PostMapping("/clock/advance")
    public ApiResponse<LocalDateTime> advance(@RequestParam long minutes) {
        return ApiResponse.ok(stationService.advanceMinutes(minutes));
    }

    @PostMapping("/piles/{pileId}/fault")
    public ApiResponse<SystemSnapshot> fault(@PathVariable String pileId,
                                             @RequestParam long minutes,
                                             @RequestParam(defaultValue = "TIME_ORDER") String strategy) {
        FaultStrategy parsed;
        try {
            parsed = FaultStrategy.valueOf(strategy.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown fault strategy: " + strategy + " (expected PRIORITY or TIME_ORDER)");
        }
        stationService.reportFault(pileId, minutes, parsed);
        return ApiResponse.ok(stationService.snapshot());
    }

    @PostMapping("/piles/{pileId}/recover")
    public ApiResponse<SystemSnapshot> recover(@PathVariable String pileId,
                                               @RequestParam(defaultValue = "TIME_ORDER") String strategy) {
        FaultStrategy parsed;
        try {
            parsed = FaultStrategy.valueOf(strategy.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown recover strategy: " + strategy + " (expected PRIORITY or TIME_ORDER)");
        }
        stationService.recover(pileId, parsed);
        return ApiResponse.ok(stationService.snapshot());
    }

    @PostMapping("/piles/{pileId}/stop")
    public ApiResponse<SystemSnapshot> stopPile(@PathVariable String pileId) {
        stationService.stopPile(pileId);
        return ApiResponse.ok(stationService.snapshot());
    }

    @PostMapping("/piles/{pileId}/start")
    public ApiResponse<SystemSnapshot> startPile(@PathVariable String pileId) {
        stationService.startPile(pileId);
        return ApiResponse.ok(stationService.snapshot());
    }

    @PostMapping("/piles/stop-all")
    public ApiResponse<SystemSnapshot> stopAllPiles() {
        stationService.stopAllPiles();
        return ApiResponse.ok(stationService.snapshot());
    }

    @PostMapping("/piles/start-all")
    public ApiResponse<SystemSnapshot> startAllPiles() {
        stationService.startAllPiles();
        return ApiResponse.ok(stationService.snapshot());
    }

    @PostMapping("/station/start")
    public ApiResponse<SystemSnapshot> startStation() {
        return ApiResponse.ok(stationService.startStation());
    }

    @PostMapping("/station/stop")
    public ApiResponse<SystemSnapshot> stopStation() {
        return ApiResponse.ok(stationService.stopStation());
    }

    @PostMapping("/schedule")
    public ApiResponse<SystemSnapshot> scheduleRequest() {
        return ApiResponse.ok(stationService.scheduleRequest());
    }

    @PostMapping("/piles/{pileId}/call-number")
    public ApiResponse<SystemSnapshot> callNumber(@PathVariable String pileId) {
        return ApiResponse.ok(stationService.callNumber(pileId));
    }

    /**
     * 题目 PDF 第 8-a 条「单次调度总充电时长最短」：等候区一次同时进多个车，按模式分配，
     * 调度目标为所有车累计等待 + 累计充电时间最短。
     * dryRun=true 仅返回计划不修改后端状态。
     */
    @PostMapping("/schedule/extended-a")
    public ApiResponse<ExtendedSchedulePlanDto> scheduleExtendedA(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return ApiResponse.ok(stationService.scheduleExtendedA(!dryRun));
    }

    /**
     * 题目 PDF 第 8-b 条「批量调度」：到达车辆数 == 全部车位时一次批量调度，不区分快慢充模式。
     */
    @PostMapping("/schedule/extended-b")
    public ApiResponse<ExtendedSchedulePlanDto> scheduleExtendedB(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return ApiResponse.ok(stationService.scheduleExtendedB(!dryRun));
    }

    @GetMapping("/persistence/counts")
    public ApiResponse<Map<String, Long>> persistenceCounts() {
        return ApiResponse.ok(persistenceReportService.counts());
    }

    @GetMapping("/reports/summary")
    public ApiResponse<AdminReportDto> report(@RequestParam(defaultValue = "ALL") String scope,
                                              @RequestParam(required = false) String pileId,
                                              @RequestParam(required = false) String vehicleId,
                                              @RequestParam(required = false) String mode) {
        return ApiResponse.ok(stationService.adminReport(scope, pileId, mode, vehicleId));
    }

    @GetMapping("/reports/details")
    public ApiResponse<List<ChargingDetail>> reportDetails(@RequestParam(defaultValue = "ALL") String scope,
                                                           @RequestParam(required = false) String pileId,
                                                           @RequestParam(required = false) String vehicleId,
                                                           @RequestParam(required = false) String mode) {
        return ApiResponse.ok(stationService.reportDetails(scope, pileId, mode, vehicleId));
    }

    @PostMapping("/parking-fee/scan")
    public ApiResponse<List<ParkingFeeDto>> scanParkingFees() {
        return ApiResponse.ok(parkingFeeService.scanAll());
    }

    @GetMapping("/parking-fees")
    public ApiResponse<List<ParkingFeeDto>> listParkingFees() {
        return ApiResponse.ok(parkingFeeService.listAll());
    }

    @GetMapping("/piles/{pileId}/queue-details")
    public ApiResponse<List<QueueDetailDto>> pileQueueDetails(@PathVariable String pileId) {
        return ApiResponse.ok(stationService.queueDetailsForPile(pileId));
    }

    @GetMapping("/waiting-area/details")
    public ApiResponse<Map<String, List<QueueDetailDto>>> waitingAreaDetails() {
        Map<String, List<QueueDetailDto>> result = new java.util.LinkedHashMap<>();
        result.put("fast", stationService.waitingAreaDetails(ChargeMode.FAST));
        result.put("slow", stationService.waitingAreaDetails(ChargeMode.SLOW));
        return ApiResponse.ok(result);
    }
}
