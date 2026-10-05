package com.salt.field.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 坨台出仓登记：客户、吨数、车牌
 */
@Getter
@Setter
public class OutboundRequest {

    @NotBlank(message = "客户名称不能为空")
    private String customer;

    @NotNull(message = "出仓吨数不能为空")
    private BigDecimal tons;

    private String plateNo;

    private String operator;
}
