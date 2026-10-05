package com.salt.field.dto;

import java.math.BigDecimal;

/**
 * 按池区统计卤度均值（JPQL 聚合投影）
 */
public record AreaBrineStat(Long areaId,
                            String areaName,
                            String areaType,
                            Long pondCount,
                            Double avgBrine,
                            BigDecimal totalMu) {
}
