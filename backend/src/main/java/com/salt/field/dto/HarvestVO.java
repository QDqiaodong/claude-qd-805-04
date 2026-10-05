package com.salt.field.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.salt.field.common.FieldDict;

import lombok.Getter;
import lombok.Setter;

/**
 * 收盐作业单视图对象（班次条用）
 */
@Getter
@Setter
public class HarvestVO {

    private Long id;
    private String taskNo;
    private Long pondId;
    private String pondNo;
    private String pondName;
    private Long platformId;
    private String platformNo;
    private LocalDate workDate;
    private String teamName;
    private BigDecimal startHour;
    private BigDecimal durationHours;
    private BigDecimal tons;
    private String workMode;
    private String workModeName;
    private String taskStatus;
    private String taskStatusName;
    private String operator;

    // ---- 雨后停工闭环（停工时持久化在作业单上，复工确认时清空） ----
    /** 停工前状态 PLANNED/WORKING，复工后恢复 */
    private String prevStatus;
    private String prevStatusName;
    /** 停工原因（含降雨量、起算时刻、最早复工时刻） */
    private String stopReason;
    /** 停工起算时刻 */
    private LocalDateTime stopStartedAt;
    /** 最早可复工时刻 */
    private LocalDateTime resumeAt;
    /** 停工时降雨量 mm */
    private BigDecimal rainfallSnapshot;
    /** 前端「确认复工」按钮可用性与逐条拦截项（RAIN_STOP 单才计算） */
    private Boolean canResume;
    private List<String> resumeBlockers;

    public static HarvestVO of(Long id,
                               String taskNo,
                               Long pondId,
                               String pondNo,
                               String pondName,
                               Long platformId,
                               String platformNo,
                               LocalDate workDate,
                               String teamName,
                               BigDecimal startHour,
                               BigDecimal durationHours,
                               BigDecimal tons,
                               String workMode,
                               String taskStatus,
                               String operator) {
        HarvestVO v = new HarvestVO();
        v.id = id;
        v.taskNo = taskNo;
        v.pondId = pondId;
        v.pondNo = pondNo;
        v.pondName = pondName;
        v.platformId = platformId;
        v.platformNo = platformNo;
        v.workDate = workDate;
        v.teamName = teamName;
        v.startHour = startHour;
        v.durationHours = durationHours;
        v.tons = tons;
        v.workMode = workMode;
        v.workModeName = FieldDict.workModeName(workMode);
        v.taskStatus = taskStatus;
        v.taskStatusName = FieldDict.taskStatusName(taskStatus);
        v.operator = operator;
        return v;
    }
}
