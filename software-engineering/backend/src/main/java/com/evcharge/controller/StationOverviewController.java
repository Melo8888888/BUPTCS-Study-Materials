package com.evcharge.controller;

import com.evcharge.dto.ApiResponse;
import com.evcharge.dto.StationConfigDto;
import com.evcharge.dto.SystemSnapshot;
import com.evcharge.service.StationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Station overview endpoints available to any authenticated user (USER or ADMIN).
 * The user-facing pages (申请、队列状态) need waiting-area counts + pile status to
 * render their info cards; that data does not contain other users' billing info
 * and is safe to expose to any logged-in user.
 */
@RestController
@RequestMapping("/api/station")
public class StationOverviewController {
    private final StationService stationService;

    public StationOverviewController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping("/overview")
    public ApiResponse<SystemSnapshot> overview() {
        return ApiResponse.ok(stationService.snapshot());
    }

    @GetMapping("/config")
    public ApiResponse<StationConfigDto> config() {
        return ApiResponse.ok(stationService.stationConfig());
    }
}
