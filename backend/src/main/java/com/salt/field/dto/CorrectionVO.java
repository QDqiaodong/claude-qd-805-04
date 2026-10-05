package com.salt.field.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.salt.field.common.FieldDict;
import com.salt.field.domain.OutboundCorrection;

import lombok.Getter;
import lombok.Setter;

/**
 * 出仓更正流水视图：车牌直改 / 冲正重记两条路径的原值、新值、操作人、时间。
 */
@Getter
@Setter
public class CorrectionVO {

    private Long id;
    /** PLATE 车牌直改 / REVERSAL 冲正重记 */
    private String changeType;
    private String changeTypeName;

    private Long recordId;
    private Long newRecordId;

    private Long oldPlatformId;
    private Long newPlatformId;
    private String oldPlatformText;
    private String newPlatformText;

    private String oldCustomer;
    private String newCustomer;
    private BigDecimal oldTons;
    private BigDecimal newTons;
    private String oldPlateNo;
    private String newPlateNo;

    private String operator;
    private LocalDateTime operatedAt;
    private String remark;

    public static CorrectionVO of(OutboundCorrection c,
                                  String oldPlatformText,
                                  String newPlatformText) {
        CorrectionVO v = new CorrectionVO();
        v.id = c.getId();
        v.changeType = c.getChangeType();
        v.changeTypeName = FieldDict.outboundChangeName(c.getChangeType());
        v.recordId = c.getRecordId();
        v.newRecordId = c.getNewRecordId();
        v.oldPlatformId = c.getOldPlatformId();
        v.newPlatformId = c.getNewPlatformId();
        v.oldPlatformText = oldPlatformText;
        v.newPlatformText = newPlatformText;
        v.oldCustomer = c.getOldCustomer();
        v.newCustomer = c.getNewCustomer();
        v.oldTons = c.getOldTons();
        v.newTons = c.getNewTons();
        v.oldPlateNo = c.getOldPlateNo();
        v.newPlateNo = c.getNewPlateNo();
        v.operator = c.getOperator();
        v.operatedAt = c.getOperatedAt();
        v.remark = c.getRemark();
        return v;
    }
}
