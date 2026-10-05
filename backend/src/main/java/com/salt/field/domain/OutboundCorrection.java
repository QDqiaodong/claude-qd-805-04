package com.salt.field.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 出仓更正流水：两条纠错路径都在本表留下不可变痕迹。
 *
 * <ul>
 *   <li>PLATE：仅车牌错误（坨台 / 客户 / 吨数均不变）→ 直接更正，不动库存；</li>
 *   <li>REVERSAL：吨数 / 坨台 / 客户错误 → 原单冲正（库存原子恢复）后按新单重记。</li>
 * </ul>
 * 每次更正都保留原值、新值、操作人和操作时刻。
 */
@Entity
@Table(name = "outbound_correction")
@Getter
@Setter
public class OutboundCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PLATE 车牌直改 / REVERSAL 冲正重记 */
    @Column(name = "change_type", nullable = false, length = 16)
    private String changeType;

    /** 被更正的出仓单：车牌直改时为本单；冲正重记时为原单 */
    @Column(name = "record_id", nullable = false)
    private Long recordId;

    /** 重记产生的新出仓单（仅冲正重记） */
    @Column(name = "new_record_id")
    private Long newRecordId;

    @Column(name = "old_platform_id")
    private Long oldPlatformId;

    @Column(name = "new_platform_id")
    private Long newPlatformId;

    @Column(name = "old_customer", length = 64)
    private String oldCustomer;

    @Column(name = "new_customer", length = 64)
    private String newCustomer;

    @Column(name = "old_tons", precision = 8, scale = 2)
    private BigDecimal oldTons;

    @Column(name = "new_tons", precision = 8, scale = 2)
    private BigDecimal newTons;

    @Column(name = "old_plate_no", length = 32)
    private String oldPlateNo;

    @Column(name = "new_plate_no", length = 32)
    private String newPlateNo;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt = LocalDateTime.now();

    @Column(name = "remark", length = 128)
    private String remark;
}
