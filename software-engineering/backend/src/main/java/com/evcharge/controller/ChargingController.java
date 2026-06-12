package com.evcharge.controller;

import com.evcharge.domain.ChargeMode;
import com.evcharge.domain.ChargingDetail;
import com.evcharge.domain.ChargingRequest;
import com.evcharge.dto.*;
import com.evcharge.service.StationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/charge")
public class ChargingController {
    private final StationService stationService;

    public ChargingController(StationService stationService) {
        this.stationService = stationService;
    }

    @PostMapping("/request")
    public ApiResponse<ChargingRequest> request(@Valid @RequestBody ChargeRequestDto dto) {
        return ApiResponse.ok(stationService.submit(dto.vehicleId(), ChargeMode.fromCode(dto.mode()), dto.amountKwh(), dto.batteryCapacity()));
    }

    @PostMapping("/change")
    public ApiResponse<ChargingRequest> change(@Valid @RequestBody ChangeRequestDto dto) {
        return ApiResponse.ok(stationService.change(dto.vehicleId(), ChargeMode.fromCode(dto.mode()), dto.amountKwh()));
    }

    @PostMapping("/cancel/{vehicleId}")
    public ApiResponse<ChargingRequest> cancel(@PathVariable String vehicleId) {
        return ApiResponse.ok(stationService.cancel(vehicleId));
    }

    @PostMapping("/end/{vehicleId}")
    public ApiResponse<ChargingRequest> end(@PathVariable String vehicleId) {
        return ApiResponse.ok(stationService.end(vehicleId));
    }

    @PostMapping("/pay/{vehicleId}")
    public ApiResponse<PaymentResultDto> pay(@PathVariable String vehicleId) {
        return ApiResponse.ok(stationService.pay(vehicleId));
    }

    @GetMapping("/details/{vehicleId}")
    public ApiResponse<List<ChargingDetail>> details(@PathVariable String vehicleId) {
        return ApiResponse.ok(stationService.detailsFor(vehicleId));
    }

    @GetMapping("/bills/{vehicleId}")
    public ApiResponse<List<BillDto>> bills(@PathVariable String vehicleId) {
        return ApiResponse.ok(stationService.billsFor(vehicleId));
    }
}
