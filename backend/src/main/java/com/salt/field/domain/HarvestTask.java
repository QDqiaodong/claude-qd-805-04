package com.salt.field.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 收盐作业单：池号、班组、作业时长、收盐吨数、机械/人工、坨台去向、作业状态
 */
@Entity
@Table(name = "harvest_task")
@Getter
@Setter
public class HarvestTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_no", unique = true, length = 32)
    private String taskNo;

    @Column(name = "pond_id", nullable = false)
    private Long pondId;

    /** 坨台去向 */
    @Column(name = "platform_id")
    private Long platformId;

    @Column(name = "work_date")
    private LocalDate workDate;

    /** 班组 */
    @Column(name = "team_name", length = 32)
    private String teamName;

    /** 开工时刻（小时，支持 5.5 这种半小时粒度） */
    @Column(name = "start_hour", precision = 4, scale = 1)
    private BigDecimal startHour;

    /** 作业时长（小时） */
    @Column(name = "duration_hours", precision = 4, scale = 1)
    private BigDecimal durationHours;

    /** 收盐吨数 */
    @Column(name = "tons", precision = 8, scale = 2)
    private BigDecimal tons;

    /** 作业方式：MACHINE 机械 / MANUAL 人工 */
    @Column(name = "work_mode", length = 16)
    private String workMode;

    /** 状态：PLANNED 待作业 / WORKING 作业中 / RAIN_STOP 雨情停工 / DONE 已完成 / CANCELED 已取消 */
    @Column(name = "task_status", length = 16)
    private String taskStatus;

    /** 雨情停工前的状态（PLANNED / WORKING），确认复工后恢复 */
    @Column(name = "prev_status", length = 16)
    private String prevStatus;

    /** 停工原因（雨情：降雨量与雨后窗口） */
    @Column(name = "stop_reason", length = 128)
    private String stopReason;

    /** 停工起算时刻 = 触发停工的有效降雨登记时刻 */
    @Column(name = "stop_started_at")
    private LocalDateTime stopStartedAt;

    /** 最早可复工时刻 = 停工起算 + RAIN_BLOCK_HOURS 小时 */
    @Column(name = "resume_at")
    private LocalDateTime resumeAt;

    /** 触发停工 / 重新起算窗口的气象记录 id */
    @Column(name = "rain_log_id")
    private Long rainLogId;

    /** 触发停工时的降雨量快照 mm */
    @Column(name = "rainfall_snapshot", precision = 6, scale = 2)
    private BigDecimal rainfallSnapshot;

    @Column(name = "operator", length = 32)
    private String operator;
}
