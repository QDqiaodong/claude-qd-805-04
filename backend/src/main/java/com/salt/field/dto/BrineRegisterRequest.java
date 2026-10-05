package com.salt.field.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 池子卤度 / 卤水深度 / 池板状况登记
 */
@Getter
@Setter
public class BrineRegisterRequest {

    @NotNull(message = "卤度不能为空")
    private BigDecimal brineDegree;

    @NotNull(message = "卤水深度不能为空")
    private BigDecimal brineDepth;

    /** GOOD 完好 / LEAK 渗漏 / REPAIR 需修 */
    private String boardStatus;

    private String operator;
}
