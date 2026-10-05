package com.salt.field.dto;

import java.math.BigDecimal;

/**
 * 按班组汇总收盐量（JPQL 聚合投影）
 */
public record TeamHarvestStat(String teamName,
                              BigDecimal totalTons,
                              BigDecimal totalHours,
                              Long taskCount) {
}
