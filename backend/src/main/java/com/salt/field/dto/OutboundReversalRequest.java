package com.salt.field.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 冲正重记请求：原单原子冲正（库存恢复）后按本单内容重记。
 * 客户/吨数/车牌为新单内容；坨台允许改为另一座（选错坨台场景）。
 */
@Getter
@Setter
public class OutboundReversalRequest {

    /** 重记的目标坨台（坨台选错时与原单不同） */
    @NotNull(message = "重记坨台不能为空")
    private Long platformId;

    @NotBlank(message = "客户名称不能为空")
    private String customer;

    @NotNull(message = "重记吨数不能为空")
    private BigDecimal tons;

    private String plateNo;

    /** 冲正 + 重记操作人 */
    private String operator;

    @Size(max = 128, message = "冲正原因最长 128 字")
    private String reason;
}
