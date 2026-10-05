package com.salt.field.dto;

import java.math.BigDecimal;

import com.salt.field.common.FieldDict;

import lombok.Getter;
import lombok.Setter;

/**
 * 坨台视图对象（3D 立方体卡片用）。
 * 库存口径：reservedTons 为登记后待复核的预占冻结量；availableTons = 库存 − 预占。
 */
@Getter
@Setter
public class PlatformVO {

    private Long id;
    private String platformNo;
    private String name;
    private String saltType;
    private String saltTypeName;
    private BigDecimal stockTons;
    /** 预占中（待复核）吨数：登记即冻结，确认后转实扣、作废后释放 */
    private BigDecimal reservedTons;
    /** 可用库存 = 账面库存 − 预占中 */
    private BigDecimal availableTons;
    private BigDecimal capacityTons;
    private BigDecimal moisture;
    private String pileShape;
    private String location;
    /** 累计实发：只统计已确认单 */
    private BigDecimal outTons;
    /** 待复核单数 */
    private Long pendingCount;
    /** 账面库存占比（0~1），立方体体积按它缩放 */
    private BigDecimal fillRatio;

    public static PlatformVO of(Long id,
                                String platformNo,
                                String name,
                                String saltType,
                                BigDecimal stockTons,
                                BigDecimal capacityTons,
                                BigDecimal moisture,
                                String pileShape,
                                String location,
                                BigDecimal outTons) {
        return of(id, platformNo, name, saltType, stockTons, capacityTons, moisture, pileShape, location,
                outTons, BigDecimal.ZERO, 0L);
    }

    public static PlatformVO of(Long id,
                                String platformNo,
                                String name,
                                String saltType,
                                BigDecimal stockTons,
                                BigDecimal capacityTons,
                                BigDecimal moisture,
                                String pileShape,
                                String location,
                                BigDecimal outTons,
                                BigDecimal reservedTons,
                                Long pendingCount) {
        PlatformVO v = new PlatformVO();
        v.id = id;
        v.platformNo = platformNo;
        v.name = name;
        v.saltType = saltType;
        v.saltTypeName = FieldDict.saltTypeName(saltType);
        v.stockTons = stockTons;
        v.capacityTons = capacityTons;
        v.moisture = moisture;
        v.pileShape = pileShape;
        v.location = location;
        v.outTons = v.nz(outTons);
        v.reservedTons = v.nz(reservedTons);
        v.pendingCount = pendingCount == null ? 0L : pendingCount;
        v.availableTons = v.nz(stockTons).subtract(v.reservedTons);
        BigDecimal cap = capacityTons == null || capacityTons.signum() <= 0 ? BigDecimal.ONE : capacityTons;
        v.fillRatio = v.nz(stockTons).divide(cap, 4, java.math.RoundingMode.HALF_UP);
        return v;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
