package com.salt.field.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salt.field.common.BizException;
import com.salt.field.common.ConflictException;
import com.salt.field.common.DispatchLifecycle;
import com.salt.field.domain.DispatchStatusLog;
import com.salt.field.domain.DispatchTask;
import com.salt.field.domain.SaltTarget;
import com.salt.field.dto.DispatchAdvanceResult;
import com.salt.field.dto.DispatchRepairRequest;
import com.salt.field.dto.DispatchStatusLogVO;
import com.salt.field.dto.DispatchStatusRequest;
import com.salt.field.dto.DispatchTaskVO;
import com.salt.field.repository.DispatchStatusLogRepository;
import com.salt.field.repository.DispatchTaskRepository;

/**
 * 派工任务生命周期核心。
 *
 * <p><b>状态机</b>（{@link DispatchLifecycle} 为唯一规则来源）：TODO → DOING → DONE，
 * 只许逐级前进；重复提交同一目标幂等成功且不留痕；跳级、倒退、未知取值、已完成后的任何
 * 修改都返回带操作指引的拒绝原因。
 *
 * <p><b>并发口径</b>：改状态先对任务行加 {@code PESSIMISTIC_WRITE} 行锁串行化，再校验调用方
 * 携带的 {@code expectedStatus} 前置条件（与出库确认同一套做法）。两名调度员同时推进时后到者
 * 拿到 409 + 最新快照；一方完成后另一方持旧页面提交，当前已是 DONE → 409，已完成结果绝不被
 * 覆盖，失败方刷新即见最新状态与生效时间。旧客户端不传 expectedStatus 时退化为状态机强校验。
 *
 * <p><b>留痕口径</b>：每次有效推进与 dispatch_task 的更新在同一事务内共同提交 / 回滚；
 * 幂等提交（含网络重试）不写新日志，重复日志无从产生。
 *
 * <p><b>历史兼容口径</b>（列表 / 详情 / 操作结果一致）：旧别名由启动对账归一化并写
 * NORMALIZE 留痕后继续可用；空值 / 非法值原样保留、标记异常、冻结流转，只能经留痕的
 * REPAIR 入口核实改入合法状态——审计真相不被悄悄改写。
 */
@Service
public class DispatchService {

    private final DispatchTaskRepository taskRepository;
    private final DispatchStatusLogRepository logRepository;
    private final FieldLogService fieldLogService;

    public DispatchService(DispatchTaskRepository taskRepository,
                           DispatchStatusLogRepository logRepository,
                           FieldLogService fieldLogService) {
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.fieldLogService = fieldLogService;
    }

    // ---------------- 查询：列表 / 详情 / 历史 ----------------

    @Transactional(readOnly = true)
    public List<DispatchTaskVO> tasks() {
        List<DispatchTaskVO> list = new ArrayList<>();
        taskRepository.findAllOrdered().forEach(t -> list.add(DispatchTaskVO.of(t)));
        return list;
    }

    @Transactional(readOnly = true)
    public DispatchTaskVO detail(Long id) {
        DispatchTask task = mustTask(id);
        return DispatchTaskVO.of(task, history(id));
    }

    @Transactional(readOnly = true)
    public List<DispatchStatusLogVO> history(Long id) {
        mustTask(id);
        List<DispatchStatusLogVO> list = new ArrayList<>();
        logRepository.findByTaskIdOrderByOperatedAtAscIdAsc(id)
                .forEach(l -> list.add(DispatchStatusLogVO.of(l)));
        return list;
    }

    // ---------------- 下达 ----------------

    @Transactional
    public DispatchTaskVO create(String taskType, String targetType, Long targetId,
                                 String crew, LocalDate planDate, String remark, String operator) {
        SaltTarget target = fieldLogService.resolveTarget(targetType, targetId);
        LocalDateTime now = LocalDateTime.now();
        String who = who(operator, "调度员");

        DispatchTask task = new DispatchTask();
        task.setTaskNo("DP" + System.currentTimeMillis() % 1000000);
        task.setTaskType(taskType);
        task.setCrew(crew == null || crew.isBlank() ? "一班" : crew.trim());
        task.setPlanDate(planDate == null ? LocalDate.now() : planDate);
        task.setTaskStatus(DispatchLifecycle.TODO);
        task.setRemark(remark);
        task.setTarget(target);
        task.setLastOperator(who);
        task.setStatusChangedAt(now);
        DispatchTask saved = taskRepository.save(task);

        writeLog(saved.getId(), "CREATE", null, DispatchLifecycle.TODO, null, who, now, "下达派工任务");
        return DispatchTaskVO.of(saved);
    }

    // ---------------- 生命周期推进 ----------------

    /**
     * 状态推进。行锁内按顺序裁决：异常冻结 → 前置条件（并发）→ 幂等 → 合法前进，
     * 业务更新与留痕同事务提交。
     */
    @Transactional
    public DispatchAdvanceResult advance(Long id, DispatchStatusRequest request) {
        String target = request.getTaskStatus() == null ? null : request.getTaskStatus().trim();
        DispatchTask task = lockTask(id); // 并发改同一任务在此串行；锁内读到的必为最新状态

        String raw = task.getTaskStatus();
        String current = DispatchLifecycle.canonical(raw);

        // ① 历史异常行：禁止任何流转，只能走修正入口（保留审计真相）
        if (current == null) {
            throw new BizException(DispatchLifecycle.rejectReason(null, raw, target));
        }

        // ② 幂等优先：目标即当前态时（含网络重试、重复点击、两人提交了相同目标）一律幂等成功，
        //    不改状态、不写留痕；响应仍带回最新快照（操作人/生效时间），后提交方看到真相
        if (DispatchLifecycle.isSame(current, target)) {
            return DispatchAdvanceResult.idempotent(task);
        }

        // ③ 前置条件：目标与当前不同、而页面快照又过期，说明发生了并发分叉改动 / 旧页面提交
        if (request.getExpectedStatus() != null && !request.getExpectedStatus().isBlank()) {
            String expected = DispatchLifecycle.canonical(request.getExpectedStatus());
            if (expected == null || !expected.equals(current)) {
                throw new ConflictException(
                        DispatchLifecycle.conflictReason(expected, current, raw),
                        DispatchTaskVO.of(task));
            }
        }

        // ④ 唯一合法前进一步；其余（跳级 / 倒退 / 未知值 / 完成后修改）给可操作拒绝原因
        if (!DispatchLifecycle.isLegal(target) || !DispatchLifecycle.isAdvance(current, target)) {
            throw new BizException(DispatchLifecycle.rejectReason(current, raw, target));
        }

        String who = who(request.getOperator(), "调度员");
        LocalDateTime now = LocalDateTime.now();
        task.setTaskStatus(target);
        task.setLastOperator(who);
        task.setStatusChangedAt(now);
        DispatchTask saved = taskRepository.save(task);

        writeLog(id, "ADVANCE", current, target, raw, who, now, null);
        return DispatchAdvanceResult.advanced(saved);
    }

    // ---------------- 历史异常修正（留痕的唯一修复入口） ----------------

    /**
     * 空值 / 非法状态人工核实后改入合法状态。仅限异常行；原值 / 操作人 / 时间 / 理由全部留痕，
     * 与状态更新同事务提交。
     */
    @Transactional
    public DispatchTaskVO repair(Long id, DispatchRepairRequest request) {
        String newStatus = request.getNewStatus() == null ? null : request.getNewStatus().trim();
        if (!DispatchLifecycle.isLegal(newStatus)) {
            throw new BizException("修正目标状态取值有误：" + newStatus
                    + "（只接受 TODO 待派 / DOING 进行中 / DONE 已完成）");
        }
        DispatchTask task = lockTask(id);
        String raw = task.getTaskStatus();
        if (DispatchLifecycle.canonical(raw) != null) {
            throw new BizException("任务 " + task.getTaskNo() + " 当前状态合法（"
                    + DispatchLifecycle.dispatchStatusName(DispatchLifecycle.canonical(raw))
                    + "），无需修正；状态推进请走「开始 / 完成」操作");
        }
        String who = who(request.getOperator(), "调度主管");
        LocalDateTime now = LocalDateTime.now();
        String reason = request.getReason() == null || request.getReason().isBlank()
                ? "历史异常状态人工核实修正" : request.getReason().trim();

        task.setTaskStatus(newStatus);
        task.setLastOperator(who);
        task.setStatusChangedAt(now);
        DispatchTask saved = taskRepository.save(task);

        writeLog(id, "REPAIR", null, newStatus, raw, who, now, reason);
        return DispatchTaskVO.of(saved);
    }

    // ---------------- 启动对账：历史别名归一化 ----------------

    /**
     * 历史数据兼容（启动执行一次）：库中为已知别名的行归一化为合法值并写 NORMALIZE 留痕，
     * 原值保留；空值 / 非法值不动（标记异常、冻结流转，等待人工 REPAIR）。
     * 已归一化的行再次启动时别名条件不再命中，天然幂等。
     *
     * @return 归一化的任务数
     */
    @Transactional
    public int reconcileLegacyStatuses() {
        int affected = 0;
        LocalDateTime now = LocalDateTime.now();
        for (DispatchTask task : taskRepository.findAll()) {
            String raw = task.getTaskStatus();
            if (DispatchLifecycle.isAlias(raw)) {
                String canonical = DispatchLifecycle.canonical(raw);
                task.setTaskStatus(canonical);
                if (task.getStatusChangedAt() == null) {
                    task.setStatusChangedAt(now);
                }
                taskRepository.save(task);
                writeLog(task.getId(), "NORMALIZE", canonical, canonical, raw,
                        "系统", now, "历史别名「" + raw.trim() + "」归一化为「"
                                + DispatchLifecycle.dispatchStatusName(canonical) + "」");
                affected++;
            }
        }
        return affected;
    }

    // ---------------- helpers ----------------

    private DispatchTask mustTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new BizException("调度任务不存在：id=" + id));
    }

    /**
     * 直接以悲观写锁加载任务行：不先 findById——否则实体会进入 Hibernate 一级缓存，
     * 后到事务拿到锁后仍读到自己缓存里的陈旧快照（并发推进漏判）。
     */
    private DispatchTask lockTask(Long id) {
        try {
            return taskRepository.lockById(id);
        } catch (JpaObjectRetrievalFailureException | EmptyResultDataAccessException e) {
            throw new BizException("调度任务不存在：id=" + id);
        }
    }

    private void writeLog(Long taskId, String type, String from, String to, String rawFrom,
                          String operator, LocalDateTime at, String remark) {
        DispatchStatusLog l = new DispatchStatusLog();
        l.setTaskId(taskId);
        l.setChangeType(type);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setRawFromStatus(rawFrom);
        l.setOperator(operator);
        l.setOperatedAt(at);
        l.setRemark(remark);
        logRepository.save(l);
    }

    private String who(String operator, String fallback) {
        return operator == null || operator.isBlank() ? fallback : operator.trim();
    }
}
