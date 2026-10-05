package com.salt.field.domain;

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
 * 派工任务状态流转留痕。
 *
 * <p>每次有效推进都在<b>同一事务</b>内追加一条：前态 / 后态 / 操作人 / 时间齐全；
 * 业务更新（dispatch_task）与留痕（本表）共同成功或共同回滚。
 * 重复提交同一目标按幂等处理，不产生新行——网络重试天然不会生成重复日志。
 *
 * <p>{@code changeType}：
 * <ul>
 *   <li>CREATE 下达任务；ADVANCE 有效推进（TODO→DOING / DOING→DONE）；</li>
 *   <li>NORMALIZE 启动对账把历史别名归一化（系统操作，原值保留）；</li>
 *   <li>REPAIR 空值 / 非法值经「修正异常」核实改入合法状态（人工留痕，原值保留）。</li>
 * </ul>
 */
@Entity
@Table(name = "dispatch_status_log")
@Getter
@Setter
public class DispatchStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** CREATE / ADVANCE / NORMALIZE / REPAIR */
    @Column(name = "change_type", nullable = false, length = 16)
    private String changeType;

    /** 归一化后的前态（CREATE / 异常行首条可为空） */
    @Column(name = "from_status", length = 16)
    private String fromStatus;

    /** 归一化后的后态（合法状态；NORMALIZE/REPAIR 之外均经状态机校验） */
    @Column(name = "to_status", length = 16)
    private String toStatus;

    /** 数据库中取到的原始前态（别名、空值、非法值原样落痕，审计真相） */
    @Column(name = "raw_from_status", length = 64)
    private String rawFromStatus;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt;

    @Column(name = "remark", length = 128)
    private String remark;
}
