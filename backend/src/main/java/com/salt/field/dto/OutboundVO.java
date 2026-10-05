package com.salt.field.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.salt.field.common.FieldDict;
import com.salt.field.domain.OutboundRecord;

import lombok.Getter;
import lombok.Setter;

/**
 * 出仓单视图对象：带状态、操作留痕字段与坨台/盐种冗余名称，复核队列与流水直接渲染。
 */
@Getter
@Setter
public class OutboundVO {

    private Long id;
    private Long platformId;
    private String platformNo;
    private String platformName;
    private String saltType;
    private String saltTypeName;
    private String customer;
    private BigDecimal tons;
    private String plateNo;
    private LocalDate outDate;
    private String operator;

    /** PENDING 待复核 / CONFIRMED 已确认 / REJECTED 已作废 / REVERSED 已冲正 */
    private String status;
    private String statusName;

    private String confirmOperator;
    private LocalDateTime confirmAt;
    private String rejectOperator;
    private LocalDateTime rejectAt;
    private String rejectReason;

    /** 冲正重记链路：reversalOfId 本单冲正自哪张原单；reversedById 本单被哪张新单冲正 */
    private Long reversalOfId;
    private Long reversedById;

    private LocalDateTime createdAt;

    public static OutboundVO of(OutboundRecord r, String platformNo, String platformName, String saltType) {
        OutboundVO v = new OutboundVO();
        v.id = r.getId();
        v.platformId = r.getPlatformId();
        v.platformNo = platformNo;
        v.platformName = platformName;
        v.saltType = saltType;
        v.saltTypeName = FieldDict.saltTypeName(saltType);
        v.customer = r.getCustomer();
        v.tons = r.getTons();
        v.plateNo = r.getPlateNo();
        v.outDate = r.getOutDate();
        v.operator = r.getOperator();
        v.status = r.getStatus();
        v.statusName = FieldDict.outboundStatusName(r.getStatus());
        v.confirmOperator = r.getConfirmOperator();
        v.confirmAt = r.getConfirmAt();
        v.rejectOperator = r.getRejectOperator();
        v.rejectAt = r.getRejectAt();
        v.rejectReason = r.getRejectReason();
        v.reversalOfId = r.getReversalOfId();
        v.reversedById = r.getReversedById();
        v.createdAt = r.getCreatedAt();
        return v;
    }
}
