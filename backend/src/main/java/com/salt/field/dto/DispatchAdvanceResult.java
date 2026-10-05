package com.salt.field.dto;

import com.salt.field.domain.DispatchTask;

/**
 * 状态推进结果：{@code idempotent=true} 表示重复提交同一目标（未改状态、未写留痕）。
 */
public record DispatchAdvanceResult(DispatchTaskVO task, boolean idempotent) {

    public static DispatchAdvanceResult advanced(DispatchTask task) {
        return new DispatchAdvanceResult(DispatchTaskVO.of(task), false);
    }

    public static DispatchAdvanceResult idempotent(DispatchTask task) {
        return new DispatchAdvanceResult(DispatchTaskVO.of(task), true);
    }
}
