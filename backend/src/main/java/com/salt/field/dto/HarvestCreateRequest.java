package com.salt.field.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 下达收盐作业
 */
@Getter
@Setter
public class HarvestCreateRequest {

    @NotNull(message = "池号不能为空")
    private Long pondId;

    @NotNull(message = "坨台去向不能为空")
    private Long platformId;

    private LocalDate workDate;

    /** 班组 */
    @NotNull(message = "班组不能为空")
    private String teamName;

    /** 开工时刻（小时） */
    @NotNull(message = "开工时刻不能为空")
    private BigDecimal startHour;

    /** 作业时长（小时） */
    @NotNull(message = "作业时长不能为空")
    private BigDecimal durationHours;

    /** 计划收盐吨数 */
    @NotNull(message = "收盐吨数不能为空")
    private BigDecimal tons;

    /** MACHINE 机械 / MANUAL 人工 */
    private String workMode;

    private String operator;
}
