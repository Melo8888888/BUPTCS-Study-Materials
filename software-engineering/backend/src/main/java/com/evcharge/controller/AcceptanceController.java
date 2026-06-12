package com.evcharge.controller;

import com.evcharge.domain.FaultStrategy;
import com.evcharge.dto.AcceptanceEventDto;
import com.evcharge.dto.ApiResponse;
import com.evcharge.dto.SystemSnapshot;
import com.evcharge.service.StationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/acceptance")
public class AcceptanceController {
    private final StationService stationService;

    public AcceptanceController(StationService stationService) {
        this.stationService = stationService;
    }

    @PostMapping("/event")
    public ApiResponse<SystemSnapshot> event(@Valid @RequestBody AcceptanceEventDto dto) {
        stationService.advanceTo(dto.time());
        stationService.applyAcceptanceEvent(dto.action(), dto.subject(), dto.mode(), dto.amount(),
                parseStrategy(dto.strategy(), FaultStrategy.TIME_ORDER));
        return ApiResponse.ok(stationService.snapshot());
    }

    @PostMapping("/run-default")
    public ApiResponse<SystemSnapshot> runDefault(@RequestParam(defaultValue = "TIME_ORDER") String strategy) {
        FaultStrategy faultStrategy = parseStrategy(strategy, FaultStrategy.TIME_ORDER);
        // 把站点配置强制回到验收默认值（N=10, M=3, 3 快充 + 2 慢充），
        // 避免上一次操作改过 config 导致默认验收结果发生偏移。
        stationService.updateStationConfig(10, 3, 3, 2, null, null);
        stationService.reset("06:00");
        for (AcceptanceEventDto event : defaultEvents()) {
            stationService.advanceTo(event.time());
            stationService.applyAcceptanceEvent(event.action(), event.subject(), event.mode(), event.amount(), faultStrategy);
        }
        return ApiResponse.ok(stationService.snapshot());
    }

    @GetMapping("/snapshot")
    public ApiResponse<SystemSnapshot> snapshot() {
        return ApiResponse.ok(stationService.snapshot());
    }

    private List<AcceptanceEventDto> defaultEvents() {
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

    private AcceptanceEventDto e(String time, String action, String subject, String mode, String amount) {
        return new AcceptanceEventDto(time, action, subject, mode, new BigDecimal(amount), null);
    }

    private FaultStrategy parseStrategy(String value, FaultStrategy fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return FaultStrategy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown fault strategy: " + value + " (expected PRIORITY or TIME_ORDER)");
        }
    }
}
