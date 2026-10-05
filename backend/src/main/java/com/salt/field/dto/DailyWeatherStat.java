package com.salt.field.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 按日期汇总蒸发量 / 降雨量（JPQL 聚合投影）
 */
public record DailyWeatherStat(LocalDate recordDate,
                               BigDecimal evaporationTotal,
                               BigDecimal rainfallTotal,
                               Double avgTemperature,
                               Double avgBrine,
                               Long recordCount) {
}
