package com.salt.field.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.salt.field.common.DispatchLifecycle;
import com.salt.field.common.FieldDict;
import com.salt.field.domain.DispatchTask;
import com.salt.field.domain.SaltTarget;

import lombok.Getter;
import lombok.Setter;

/**
 * 场务调度任务视图对象（第二个 {@code @Any} 落点的回显）。
 *
 * <p>状态字段在列表 / 详情 / 操作结果中严格使用同一口径（{@link DispatchLifecycle}）：
 * <ul>
 *   <li>{@link #taskStatus} 永远是合法状态（TODO/DOING/DONE）；空值 / 非法值时为 {@code null}；</li>
 *   <li>{@link #rawTaskStatus} 是库中原始值，{@link #abnormal} 行为 true 时禁止任何流转；</li>
 *   <li>{@link #nextStatus} 是当前唯一合法的下一步（异常 / 已完成时为空，前端据此只出合法按钮）。</li>
 * </ul>
 */
@Getter
@Setter
public class DispatchTaskVO {

    private Long id;
    private String taskNo;
    private String taskType;
    private String taskTypeName;
    private String crew;
    private LocalDate planDate;

    /** 归一化后的合法状态；历史空值 / 非法值时为 null（配合 abnormal 展示） */
    private String taskStatus;
    private String taskStatusName;
    /** 库中原始状态（别名 / 空值 / 非法值原样回显，审计真相） */
    private String rawTaskStatus;
    /** 是否历史异常行（空值 / 非法值；冻结流转，只能走「修正异常」） */
    private boolean abnormal;
    /** 异常统一提示（列表角标 / 详情 / 操作失败三处同文案） */
    private String abnormalHint;
    /** 是否旧别名（已按最接近合法状态归一化可用） */
    private boolean alias;

    /** 唯一合法的下一步状态及其名称（异常 / 已完成 = null） */
    private String nextStatus;
    private String nextStatusName;

    private String lastOperator;
    private LocalDateTime statusChangedAt;

    private String remark;

    private String targetType;
    private String targetTypeName;
    private Long targetId;
    private String targetName;

    /** 详情接口附带状态流转历史；列表接口为空 */
    private List<DispatchStatusLogVO> history;

    public static DispatchTaskVO of(DispatchTask t) {
        return of(t, null);
    }

    public static DispatchTaskVO of(DispatchTask t, List<DispatchStatusLogVO> history) {
        DispatchTaskVO v = new DispatchTaskVO();
        v.id = t.getId();
        v.taskNo = t.getTaskNo();
        v.taskType = t.getTaskType();
        v.taskTypeName = FieldDict.dispatchTypeName(t.getTaskType());
        v.crew = t.getCrew();
        v.planDate = t.getPlanDate();
        v.remark = t.getRemark();
        v.lastOperator = t.getLastOperator();
        v.statusChangedAt = t.getStatusChangedAt();
        v.history = history;

        String raw = t.getTaskStatus();
        v.rawTaskStatus = raw;
        String canonical = DispatchLifecycle.canonical(raw);
        v.abnormal = canonical == null;
        v.alias = !v.abnormal && DispatchLifecycle.isAlias(raw);
        v.taskStatus = canonical;
        v.taskStatusName = DispatchLifecycle.dispatchStatusName(canonical);
        if (v.abnormal) {
            v.abnormalHint = DispatchLifecycle.abnormalHint(raw);
        } else {
            v.nextStatus = DispatchLifecycle.nextOf(canonical);
            v.nextStatusName = DispatchLifecycle.dispatchStatusName(v.nextStatus);
        }

        SaltTarget target = t.getTarget();
        if (target != null) {
            v.targetType = target.targetType();
            v.targetTypeName = FieldDict.targetTypeName(v.targetType);
            v.targetId = target.getId();
            v.targetName = target.getName();
        } else {
            v.targetTypeName = "未关联";
        }
        return v;
    }
}
