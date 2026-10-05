package com.salt.field.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * 坨台出仓记录：客户、吨数、车牌。
 *
 * <p>库存口径＝「登记预占、确认实扣」：
 * <ul>
 *   <li>{@link #status} = PENDING：登记后只冻结（预占）吨数，账面库存不动，参与「可用库存」扣减；</li>
 *   <li>CONFIRMED：复核确认后才真正扣减库存，累计发运只统计它；</li>
 *   <li>REJECTED：待复核单作废，预占释放；</li>
 *   <li>REVERSED：已确认单被冲正，库存原子恢复，永不删除。</li>
 * </ul>
 */
@Entity
@Table(name = "outbound_record")
@Getter
@Setter
public class OutboundRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_id", nullable = false)
    private Long platformId;

    @Column(name = "customer", length = 64)
    private String customer;

    @Column(name = "tons", precision = 8, scale = 2)
    private BigDecimal tons;

    @Column(name = "plate_no", length = 32)
    private String plateNo;

    @Column(name = "out_date")
    private LocalDate outDate;

    /** 登记司磅员 */
    @Column(name = "operator", length = 32)
    private String operator;

    /** 单据状态：PENDING 待复核 / CONFIRMED 已确认 / REJECTED 已作废 / REVERSED 已冲正 */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "confirm_operator", length = 32)
    private String confirmOperator;

    @Column(name = "confirm_at")
    private LocalDateTime confirmAt;

    @Column(name = "reject_operator", length = 32)
    private String rejectOperator;

    @Column(name = "reject_at")
    private LocalDateTime rejectAt;

    @Column(name = "reject_reason", length = 128)
    private String rejectReason;

    /** 冲正重记：本单（重记新单）由哪张原单冲正而来 */
    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    /** 冲正重记：本单（原单）被哪张重记新单冲正 */
    @Column(name = "reversed_by_id")
    private Long reversedById;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * JPA 乐观锁版本号。与坨台/单据行悲观锁配合：并发确认 / 冲正时，第二个事务 UPDATE 单据
     * 会因版本号已变而抛 OptimisticLockException（由数据库层兜底拒绝），杜绝「同单确认两次 /
     * 状态读到 PENDING 旧快照」导致的重复实扣。
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
