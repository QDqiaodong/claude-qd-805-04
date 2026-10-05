package com.salt.field.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 登记一条作业记录：targetType + targetId 可以指向池子 / 坨台 / 设备中的任意一种
 */
@Getter
@Setter
public class FieldLogCreateRequest {

    /** BRINE 测卤 / HARVEST 收盐 / OUTBOUND 出仓 / REPAIR 修池 / INSPECT 巡检 */
    @NotNull(message = "记录类型不能为空")
    private String logType;

    /** POND / PLATFORM / EQUIPMENT */
    @NotNull(message = "关联对象类型不能为空")
    private String targetType;

    @NotNull(message = "关联对象不能为空")
    private Long targetId;

    private String content;
    private String operator;
    private LocalDateTime logTime;
}
