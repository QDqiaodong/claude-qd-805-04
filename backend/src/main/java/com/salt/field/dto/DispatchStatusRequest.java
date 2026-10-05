package com.salt.field.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 派工状态推进请求。
 *
 * @param taskStatus     目标状态，只接受 TODO / DOING / DONE
 * @param expectedStatus 操作人页面上看到的当前状态（乐观前置条件，防旧页面覆盖）。
 *                       旧客户端不传时退化为状态机强校验：倒退 / 跳级 / 完成后修改仍全部拒绝；
 *                       传入时必须与数据库最新状态一致，否则 409 并回带最新快照。
 * @param operator       操作人（调度员）
 */
@Getter
@Setter
public class DispatchStatusRequest {

    @NotNull(message = "目标状态不能为空")
    private String taskStatus;

    private String expectedStatus;

    private String operator;
}
