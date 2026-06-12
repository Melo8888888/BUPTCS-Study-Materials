package com.evcharge.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一个时间段内同一车辆的账单聚合：把多张详单汇总成一份"账单"对象，
 * 对应概要设计 v3 UC_08 中 `Create_Bill` 的语义。
 */
public record BillAggregateDto(
        String billId,
        String vehicleId,
        String period,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        int detailCount,
        BigDecimal totalKwh,
        long totalDurationMinutes,
        BigDecimal totalChargeFee,
        BigDecimal totalServiceFee,
        BigDecimal totalFee,
        boolean allPaid,
        BigDecimal unpaidAmount,
        List<BillDto> details
) {}
