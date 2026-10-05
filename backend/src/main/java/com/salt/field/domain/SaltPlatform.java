package com.salt.field.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 坨台：盐种、库存吨、垛形、含水率。也是 {@code @Any} 的一类目标对象（target_type = PLATFORM）。
 */
@Entity
@Table(name = "salt_platform")
@Getter
@Setter
public class SaltPlatform extends SaltTarget {

    @Column(name = "platform_no", nullable = false, unique = true, length = 32)
    private String platformNo;

    /** 盐种：INDUSTRIAL 工业盐 / SUN 日晒盐 / REFINED 精制盐 */
    @Column(name = "salt_type", length = 16)
    private String saltType;

    /** 库存（吨） */
    @Column(name = "stock_tons", precision = 10, scale = 2)
    private BigDecimal stockTons;

    /** 容量（吨） */
    @Column(name = "capacity_tons", precision = 10, scale = 2)
    private BigDecimal capacityTons;

    /** 垛形：锥形垛 / 长条垛 / 方垛 */
    @Column(name = "pile_shape", length = 32)
    private String pileShape;

    /** 含水率 % */
    @Column(name = "moisture", precision = 5, scale = 2)
    private BigDecimal moisture;

    @Column(name = "location", length = 64)
    private String location;

    @Override
    public String targetType() {
        return "PLATFORM";
    }
}
