package com.evcharge.dto;

import java.util.List;

/**
 * 题目 PDF 第 8 条"扩展调度请求"的批量调度结果。
 *
 * 8-a: 同模式分组，单次同时调度（按桩类型分配）；
 * 8-b: 不区分模式，所有桩通用。
 * 调度目标：minimize Σ(每车等待时间 + 充电时间)
 */
public record ExtendedSchedulePlanDto(
        String strategy,           // "MODE_AWARE" (8-a) 或 "MODE_FREE" (8-b)
        long totalCompletionMinutes,
        int vehicleCount,
        int pileCount,
        boolean applied,           // false = dryRun preview
        List<Assignment> assignments
) {
    public record Assignment(
            String vehicleId,
            String queueNo,
            String mode,
            String pileId,
            int slotIndex,
            long waitMinutes,
            long chargeMinutes,
            long completionMinutes  // wait + charge
    ) {}
}
