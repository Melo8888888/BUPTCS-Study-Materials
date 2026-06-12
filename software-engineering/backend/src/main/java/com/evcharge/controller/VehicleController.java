package com.evcharge.controller;

import com.evcharge.domain.ChargeMode;
import com.evcharge.domain.ChargingRequest;
import com.evcharge.dto.*;
import com.evcharge.service.ParkingFeeService;
import com.evcharge.service.StationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {
    private final StationService stationService;
    private final ParkingFeeService parkingFeeService;

    public VehicleController(StationService stationService, ParkingFeeService parkingFeeService) {
        this.stationService = stationService;
        this.parkingFeeService = parkingFeeService;
    }

    @PostMapping("/{vehicleId}/requests")
    public ApiResponse<ChargingRequest> submit(@PathVariable String vehicleId, @Valid @RequestBody VehicleChargeRequestDto dto) {
        return ApiResponse.ok(stationService.submit(vehicleId, ChargeMode.fromCode(dto.mode()), dto.amountKwh(), dto.batteryCapacity()));
    }

    @PostMapping("/{vehicleId}/requests/change")
    public ApiResponse<ChargingRequest> change(@PathVariable String vehicleId, @Valid @RequestBody VehicleChangeRequestDto dto) {
        return ApiResponse.ok(stationService.change(vehicleId, ChargeMode.fromCode(dto.mode()), dto.amountKwh()));
    }

    @PostMapping("/{vehicleId}/requests/cancel")
    public ApiResponse<ChargingRequest> cancel(@PathVariable String vehicleId) {
        return ApiResponse.ok(stationService.cancel(vehicleId));
    }

    @PostMapping("/{vehicleId}/charging/end")
    public ApiResponse<ChargingRequest> end(@PathVariable String vehicleId) {
        return ApiResponse.ok(stationService.end(vehicleId));
    }

    @GetMapping("/{vehicleId}/status")
    public ApiResponse<VehicleStatusDto> status(@PathVariable String vehicleId) {
        return ApiResponse.ok(stationService.vehicleStatus(vehicleId));
    }

    @GetMapping("/{vehicleId}/bills")
    public ApiResponse<List<BillDto>> bills(@PathVariable String vehicleId) {
        return ApiResponse.ok(stationService.billsFor(vehicleId));
    }

    @GetMapping("/{vehicleId}/bill-aggregate")
    public ApiResponse<BillAggregateDto> billAggregate(@PathVariable String vehicleId,
                                                       @RequestParam(defaultValue = "ALL") String scope) {
        return ApiResponse.ok(stationService.billAggregate(vehicleId, scope));
    }

    @PostMapping("/{vehicleId}/bills/pay")
    public ApiResponse<PaymentResultDto> pay(@PathVariable String vehicleId) {
        return ApiResponse.ok(stationService.pay(vehicleId));
    }

    @GetMapping("/{vehicleId}/parking-fees")
    public ApiResponse<List<ParkingFeeDto>> parkingFees(@PathVariable String vehicleId) {
        return ApiResponse.ok(parkingFeeService.previewForVehicle(vehicleId));
    }

    @PostMapping("/{vehicleId}/parking-fee/pay/{feeId}")
    public ApiResponse<ParkingFeeDto> payParkingFee(@PathVariable String vehicleId, @PathVariable String feeId) {
        return ApiResponse.ok(parkingFeeService.payOne(vehicleId, feeId));
    }
}
