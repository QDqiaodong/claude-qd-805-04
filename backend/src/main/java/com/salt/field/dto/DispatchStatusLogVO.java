package com.salt.field.dto;

import com.salt.field.common.DispatchLifecycle;
import com.salt.field.domain.DispatchStatusLog;

import lombok.Getter;
import lombok.Setter;

/**
 * 派工状态流转留痕视图：前态 → 后态、原始值、操作人、时间、变更类型。
 */
@Getter
@Setter
public class DispatchStatusLogVO {

    private Long id;
    private String changeType;
    private String changeTypeName;
    private String fromStatus;
    private String fromStatusName;
    private String toStatus;
    private String toStatusName;
    private String rawFromStatus;
    private String operator;
    private String operatedAt;
    private String remark;

    public static DispatchStatusLogVO of(DispatchStatusLog l) {
        DispatchStatusLogVO v = new DispatchStatusLogVO();
        v.id = l.getId();
        v.changeType = l.getChangeType();
        v.changeTypeName = changeTypeName(l.getChangeType());
        v.fromStatus = l.getFromStatus();
        v.fromStatusName = DispatchLifecycle.dispatchStatusName(l.getFromStatus());
        v.toStatus = l.getToStatus();
        v.toStatusName = DispatchLifecycle.dispatchStatusName(l.getToStatus());
        v.rawFromStatus = l.getRawFromStatus();
        v.operator = l.getOperator();
        v.operatedAt = l.getOperatedAt() == null ? null : l.getOperatedAt().toString().replace('T', ' ');
        v.remark = l.getRemark();
        return v;
    }

    public static String changeTypeName(String t) {
        if ("CREATE".equals(t)) {
            return "下达任务";
        }
        if ("ADVANCE".equals(t)) {
            return "状态推进";
        }
        if ("NORMALIZE".equals(t)) {
            return "历史别名归一化";
        }
        if ("REPAIR".equals(t)) {
            return "异常状态修正";
        }
        return t;
    }
}
