package com.salt.field.dto;

import java.math.BigDecimal;

/**
 * 坨台库存、预占与发运汇总（JPQL 聚合投影）。
 *
 * @param stockTons    账面库存（仅 CONFIRMED 实扣后才减少）
 * @param outTons      累计实发（只统计已确认单）
 * @param pendingCount 待复核单数
 * @param reservedTons 预占中吨数（待复核单冻结量，未扣账面）
 */
public record PlatformStockStat(Long platformId,
                                String platformNo,
                                String platformName,
                                String saltType,
                                BigDecimal stockTons,
                                BigDecimal capacityTons,
                                BigDecimal outTons,
                                Long pendingCount,
                                BigDecimal reservedTons) {

    /** 可用库存 = 账面库存 − 预占中 */
    public BigDecimal availableTons() {
        BigDecimal stock = stockTons == null ? BigDecimal.ZERO : stockTons;
        BigDecimal reserved = reservedTons == null ? BigDecimal.ZERO : reservedTons;
        return stock.subtract(reserved);
    }
}
