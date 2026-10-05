package com.salt.field.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 出仓复核：确认 / 作废（释放预占） */
@Getter
@Setter
public class OutboundReviewRequest {

    /** 复核操作人（作废时为作废操作人） */
    @NotBlank(message = "复核操作人不能为空")
    private String operator;

    /** 作废原因（仅作废使用） */
    @Size(max = 128, message = "作废原因最长 128 字")
    private String reason;
}
