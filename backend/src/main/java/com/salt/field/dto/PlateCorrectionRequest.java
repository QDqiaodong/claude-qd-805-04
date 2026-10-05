package com.salt.field.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 仅车牌错误的直接更正：坨台 / 客户 / 吨数均不变，故不接收这些字段，
 * 后端再次校验三者与原单一致，杜绝借「车牌直改」通道改吨数或坨台。
 */
@Getter
@Setter
public class PlateCorrectionRequest {

    @NotBlank(message = "新车牌不能为空")
    @Size(max = 32, message = "车牌最长 32 字")
    private String newPlateNo;

    /** 更正操作人 */
    @NotBlank(message = "操作人不能为空")
    private String operator;

    @Size(max = 128, message = "备注最长 128 字")
    private String remark;
}
