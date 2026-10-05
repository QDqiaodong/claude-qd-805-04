package com.salt.field.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 调度员确认雨后复工
 */
@Getter
@Setter
public class HarvestResumeRequest {

    /** 复工确认人（默认「调度员」） */
    private String operator;
}
