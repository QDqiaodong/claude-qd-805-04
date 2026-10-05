package com.salt.field.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 首屏概览：池区数 / 池子数 / 当日收盐 / 库存 / 天气摘要
 */
@Getter
@Setter
public class OverviewVO {

    private Long areaCount;
    private Long pondCount;
    private Long platformCount;
    private BigDecimal stockTons;
    private BigDecimal todayTons;
    private BigDecimal planTons;
    private BigDecimal todayEvaporation;
    private BigDecimal todayRainfall;
    private Long waitingPondCount;
    private Long repairingPondCount;
    private List<String> teams;
}
