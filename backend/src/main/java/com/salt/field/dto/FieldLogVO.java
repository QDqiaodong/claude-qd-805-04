package com.salt.field.dto;

import java.time.LocalDateTime;

import com.salt.field.common.FieldDict;
import com.salt.field.domain.FieldLog;
import com.salt.field.domain.SaltTarget;

import lombok.Getter;
import lombok.Setter;

/**
 * 作业记录视图对象：把 {@code @Any} 多态关联解析成「目标类型 + 目标名称」返回给前端
 */
@Getter
@Setter
public class FieldLogVO {

    private Long id;
    private String logType;
    private String logTypeName;
    private String content;
    private String operator;
    private LocalDateTime logTime;

    /** 多态关联：目标类型编码（POND / PLATFORM / EQUIPMENT） */
    private String targetType;
    /** 多态关联：目标类型中文名 */
    private String targetTypeName;
    /** 多态关联：目标主键 */
    private Long targetId;
    /** 多态关联：目标名称 */
    private String targetName;

    public static FieldLogVO of(FieldLog log) {
        FieldLogVO v = new FieldLogVO();
        v.id = log.getId();
        v.logType = log.getLogType();
        v.logTypeName = FieldDict.logTypeName(log.getLogType());
        v.content = log.getContent();
        v.operator = log.getOperator();
        v.logTime = log.getLogTime();
        SaltTarget target = log.getTarget();
        if (target != null) {
            v.targetType = target.targetType();
            v.targetTypeName = FieldDict.targetTypeName(v.targetType);
            v.targetId = target.getId();
            v.targetName = target.getName();
        } else {
            v.targetTypeName = "未关联";
        }
        return v;
    }
}
