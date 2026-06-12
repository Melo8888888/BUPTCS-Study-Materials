package com.evcharge.dto;

import java.util.List;

public record VehicleStatusDto(
        String vehicleId,
        RequestSnapshot activeRequest,
        List<RequestSnapshot> requests,
        List<BillDto> bills,
        long queueAhead,
        String message
) {}
