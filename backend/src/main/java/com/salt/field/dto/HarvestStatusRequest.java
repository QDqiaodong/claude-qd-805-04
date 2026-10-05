package com.salt.field.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 改作业状态：PLANNED / WORKING / DONE / CANCELED
 */
@Getter
@Setter
public class HarvestStatusRequest {

    @NotNull(message = "作业状态不能为空")
    private String taskStatus;
}
