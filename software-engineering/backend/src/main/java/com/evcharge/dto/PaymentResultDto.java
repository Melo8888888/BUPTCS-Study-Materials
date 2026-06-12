package com.evcharge.dto;

import java.math.BigDecimal;
import java.util.List;

public record PaymentResultDto(
        String vehicleId,
        BigDecimal paidAmount,
        int paidBillCount,
        List<BillDto> bills
) {}
