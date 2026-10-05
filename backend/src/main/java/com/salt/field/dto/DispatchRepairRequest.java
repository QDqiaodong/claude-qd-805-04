package com.salt.field.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 历史异常状态（空值 / 非法值）人工修正请求：原值保留在留痕里，只允许改入合法状态。
 */
@Getter
@Setter
public class DispatchRepairRequest {

    @NotNull(message = "修正后的合法状态不能为空（TODO 待派 / DOING 进行中 / DONE 已完成）")
    private String newStatus;

    @NotNull(message = "请填写修正操作人（调度主管）")
    private String operator;

    /** 修正理由，记入留痕 */
    private String reason;
}
