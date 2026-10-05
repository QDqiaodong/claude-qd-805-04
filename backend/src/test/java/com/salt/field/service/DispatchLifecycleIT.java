package com.salt.field.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.salt.field.common.BizException;
import com.salt.field.common.ConflictException;
import com.salt.field.common.DispatchLifecycle;
import com.salt.field.domain.DispatchTask;
import com.salt.field.domain.Pond;
import com.salt.field.dto.DispatchRepairRequest;
import com.salt.field.dto.DispatchStatusLogVO;
import com.salt.field.dto.DispatchStatusRequest;
import com.salt.field.dto.DispatchTaskVO;
import com.salt.field.repository.DispatchStatusLogRepository;
import com.salt.field.repository.DispatchTaskRepository;
import com.salt.field.repository.PondRepository;

/**
 * 派工生命周期、幂等、并发、留痕、历史兼容的集成测试（H2 + 真实事务 + 悲观行锁）。
 */
@SpringBootTest
class DispatchLifecycleIT {

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private DispatchTaskRepository taskRepository;

    @Autowired
    private DispatchStatusLogRepository logRepository;

    @Autowired
    private PondRepository pondRepository;

    private Long taskId;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        logRepository.deleteAll();
        Pond pond = new Pond();
        pond.setName("测试池");
        pond.setPondNo("P-TEST-" + System.nanoTime());
        pond = pondRepository.save(pond);
        DispatchTaskVO vo = dispatchService.create("FIX", "POND", pond.getId(), "一班", null, "测试", "调度员甲");
        taskId = vo.getId();
    }

    private DispatchStatusRequest req(String target, String expected, String operator) {
        DispatchStatusRequest r = new DispatchStatusRequest();
        r.setTaskStatus(target);
        r.setExpectedStatus(expected);
        r.setOperator(operator);
        return r;
    }

    // ---------------- 正常推进 ----------------

    @Test
    void advances_todo_to_doing_to_done_and_logs_each_step() {
        var r1 = dispatchService.advance(taskId, req("DOING", "TODO", "调度员甲"));
        assertThat(r1.idempotent()).isFalse();
        assertThat(r1.task().getTaskStatus()).isEqualTo("DOING");
        assertThat(r1.task().getLastOperator()).isEqualTo("调度员甲");
        assertThat(r1.task().getStatusChangedAt()).isNotNull();
        assertThat(r1.task().getNextStatus()).isEqualTo("DONE");

        var r2 = dispatchService.advance(taskId, req("DONE", "DOING", "调度员甲"));
        assertThat(r2.task().getTaskStatus()).isEqualTo("DONE");
        assertThat(r2.task().getNextStatus()).isNull();

        List<DispatchStatusLogVO> history = dispatchService.history(taskId);
        // CREATE + 两次 ADVANCE
        assertThat(history).hasSize(3);
        assertThat(history).extracting(DispatchStatusLogVO::getChangeType)
                .containsExactly("CREATE", "ADVANCE", "ADVANCE");
        assertThat(history.get(1).getFromStatus()).isEqualTo("TODO");
        assertThat(history.get(1).getToStatus()).isEqualTo("DOING");
        assertThat(history.get(1).getOperator()).isEqualTo("调度员甲");
    }

    // ---------------- 幂等：重复提交 / 网络重试 ----------------

    @Test
    void duplicate_same_target_is_idempotent_and_writes_no_extra_log() {
        dispatchService.advance(taskId, req("DOING", "TODO", "甲"));
        long logsAfterAdvance = logRepository.count();

        // 同一目标重复提交（模拟网络重试、双击）
        var dup1 = dispatchService.advance(taskId, req("DOING", "DOING", "甲"));
        var dup2 = dispatchService.advance(taskId, req("DOING", null, "甲")); // 旧客户端不带 expected
        assertThat(dup1.idempotent()).isTrue();
        assertThat(dup2.idempotent()).isTrue();
        assertThat(dup1.task().getTaskStatus()).isEqualTo("DOING");
        assertThat(logRepository.count()).isEqualTo(logsAfterAdvance);
    }

    // ---------------- 拒绝：跳级 / 倒退 / 未知 / 完成后修改 ----------------

    @Test
    void rejects_skip_level_todo_to_done() {
        assertThatThrownBy(() -> dispatchService.advance(taskId, req("DONE", "TODO", "甲")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("跳级");
        assertThat(taskRepository.findById(taskId).orElseThrow().getTaskStatus()).isEqualTo("TODO");
        assertThat(logRepository.count()).isEqualTo(1); // 只有 CREATE
    }

    @Test
    void rejects_backward_transitions() {
        dispatchService.advance(taskId, req("DOING", "TODO", "甲"));
        assertThatThrownBy(() -> dispatchService.advance(taskId, req("TODO", "DOING", "甲")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("退回");

        dispatchService.advance(taskId, req("DONE", "DOING", "甲"));
        for (String target : new String[]{"TODO", "DOING"}) {
            assertThatThrownBy(() -> dispatchService.advance(taskId, req(target, "DONE", "甲")))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("已完成");
        }
    }

    @Test
    void rejects_unknown_target_value() {
        assertThatThrownBy(() -> dispatchService.advance(taskId, req("DOINGG", "TODO", "甲")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("目标状态取值有误");
        assertThatThrownBy(() -> dispatchService.advance(taskId, req("", "TODO", "甲")))
                .isInstanceOf(BizException.class);
    }

    @Test
    void rollback_together_when_advance_rejected_leaves_no_log_and_old_status() {
        long before = logRepository.count();
        assertThatThrownBy(() -> dispatchService.advance(taskId, req("DONE", "TODO", "甲")))
                .isInstanceOf(BizException.class);
        assertThat(taskRepository.findById(taskId).orElseThrow().getTaskStatus()).isEqualTo("TODO");
        assertThat(logRepository.count()).isEqualTo(before);
    }

    // ---------------- 并发：两人同时改 / 旧页面覆盖 ----------------

    @Test
    void two_dispatchers_concurrent_only_one_wins_and_done_is_never_overwritten() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger advanced = new AtomicInteger();
        AtomicInteger idempotent = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger(); // 409 分叉 / 400 跳级
        AtomicReference<String> failure = new AtomicReference<>();

        // 都持 TODO 快照：一半推进 DOING、一半跳级 DONE。行锁串行后：
        // 恰好 1 次真实推进；同目标者幂等；分叉/跳级者被拒；最终态恒为 DOING
        for (int i = 0; i < threads; i++) {
            final String target = (i % 2 == 0) ? "DOING" : "DONE";
            pool.submit(() -> {
                try {
                    start.await();
                    var r = dispatchService.advance(taskId, req(target, "TODO", "调度员"));
                    if (r.idempotent()) {
                        idempotent.incrementAndGet();
                    } else {
                        advanced.incrementAndGet();
                    }
                } catch (ConflictException | BizException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    failure.compareAndSet(null, e.toString());
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNull();
        assertThat(advanced.get()).isEqualTo(1);
        assertThat(advanced.get() + idempotent.get() + rejected.get()).isEqualTo(threads);
        assertThat(taskRepository.findById(taskId).orElseThrow().getTaskStatus()).isEqualTo("DOING");
        // 只有一次 ADVANCE 留痕（幂等不写、被拒回滚）
        long advances = logRepository.findAll().stream().filter(l -> "ADVANCE".equals(l.getChangeType())).count();
        assertThat(advances).isEqualTo(1);

        // 另一方在旧页面（快照 DOING）上尝试退回 TODO：409（状态已被推进到 DONE），DONE 不被覆盖
        dispatchService.advance(taskId, req("DONE", "DOING", "乙"));
        assertThatThrownBy(() -> dispatchService.advance(taskId, req("TODO", "DOING", "乙")))
                .isInstanceOf(ConflictException.class);
        assertThat(taskRepository.findById(taskId).orElseThrow().getTaskStatus()).isEqualTo("DONE");
    }

    @Test
    void conflict_response_carries_latest_snapshot_and_effective_time() {
        dispatchService.advance(taskId, req("DOING", "TODO", "甲"));
        // 旧页面快照 TODO 提交一个不同目标 DONE：分叉冲突 409，回带最新快照 DOING + 生效时间
        assertThatThrownBy(() -> dispatchService.advance(taskId, req("DONE", "TODO", "乙")))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> {
                    ConflictException ce = (ConflictException) e;
                    DispatchTaskVO snap = (DispatchTaskVO) ce.getSnapshot();
                    assertThat(snap.getTaskStatus()).isEqualTo("DOING");
                    assertThat(snap.getStatusChangedAt()).isNotNull();
                    assertThat(snap.getLastOperator()).isEqualTo("甲");
                });
    }

    @Test
    void same_target_retry_on_stale_page_is_idempotent_even_if_expected_differs() {
        dispatchService.advance(taskId, req("DOING", "TODO", "甲"));
        // 乙旧页面停留 TODO，网络重试的却是与当前相同的目标 DOING：幂等成功、无留痕
        var r = dispatchService.advance(taskId, req("DOING", "TODO", "乙"));
        assertThat(r.idempotent()).isTrue();
        assertThat(r.task().getTaskStatus()).isEqualTo("DOING");
        long advances = logRepository.findAll().stream().filter(l -> "ADVANCE".equals(l.getChangeType())).count();
        assertThat(advances).isEqualTo(1);
    }

    // ---------------- 历史兼容：别名归一化 ----------------

    @Test
    void reconciler_normalizes_legacy_aliases_once_and_keeps_them_usable() {
        taskRepository.deleteAll();
        logRepository.deleteAll();
        DispatchTask alias1 = persistRaw("DP-A1", "DO");
        DispatchTask alias2 = persistRaw("DP-A2", "已完成");
        DispatchTask alias3 = persistRaw("DP-A3", "running");

        int n = dispatchService.reconcileLegacyStatuses();
        assertThat(n).isEqualTo(3);
        assertThat(taskRepository.findById(alias1.getId()).orElseThrow().getTaskStatus()).isEqualTo("DOING");
        assertThat(taskRepository.findById(alias2.getId()).orElseThrow().getTaskStatus()).isEqualTo("DONE");

        // 归一化后可继续流转
        var vo = dispatchService.advance(alias1.getId(), req("DONE", "DOING", "主管"));
        assertThat(vo.task().getTaskStatus()).isEqualTo("DONE");

        // 再次对账幂等：不重复留痕
        long logs = logRepository.count();
        assertThat(dispatchService.reconcileLegacyStatuses()).isZero();
        assertThat(logRepository.count()).isEqualTo(logs);

        // NORMALIZE 留痕保留原值
        List<DispatchStatusLogVO> h = dispatchService.history(alias3.getId());
        assertThat(h).extracting(DispatchStatusLogVO::getChangeType).contains("NORMALIZE");
        assertThat(h.get(0).getRawFromStatus()).isEqualTo("running");
    }

    // ---------------- 历史兼容：空值 / 非法值冻结 + 修正 ----------------

    @Test
    void null_and_illegal_statuses_are_flagged_frozen_and_repair_restores_with_audit() {
        taskRepository.deleteAll();
        logRepository.deleteAll();
        DispatchTask nul = persistRaw("DP-N1", null);
        DispatchTask bad = persistRaw("DP-N2", "XYZ");

        // 列表 / 详情口径一致：abnormal=true、taskStatus=null、原始值保留
        DispatchTaskVO nv = dispatchService.detail(nul.getId());
        assertThat(nv.isAbnormal()).isTrue();
        assertThat(nv.getTaskStatus()).isNull();
        assertThat(nv.getRawTaskStatus()).isNull();
        assertThat(nv.getNextStatus()).isNull();
        assertThat(nv.getAbnormalHint()).contains("空值");

        DispatchTaskVO bv = dispatchService.detail(bad.getId());
        assertThat(bv.isAbnormal()).isTrue();
        assertThat(bv.getRawTaskStatus()).isEqualTo("XYZ");
        assertThat(bv.getAbnormalHint()).contains("XYZ");

        // 冻结：任何流转尝试都被拒绝
        assertThatThrownBy(() -> dispatchService.advance(nul.getId(), req("DOING", null, "甲")))
                .isInstanceOf(BizException.class).hasMessageContaining("历史异常值");
        assertThatThrownBy(() -> dispatchService.advance(bad.getId(), req("DOING", null, "甲")))
                .isInstanceOf(BizException.class).hasMessageContaining("历史异常值");

        // 非法修复目标仍被拒
        DispatchRepairRequest badTarget = new DispatchRepairRequest();
        badTarget.setNewStatus("WAT");
        badTarget.setOperator("主管");
        assertThatThrownBy(() -> dispatchService.repair(bad.getId(), badTarget))
                .isInstanceOf(BizException.class);

        // 留痕修正后恢复可用
        DispatchRepairRequest repair = new DispatchRepairRequest();
        repair.setNewStatus("DOING");
        repair.setOperator("调度主管");
        repair.setReason("比对纸质派工单确认");
        var fixed = dispatchService.repair(nul.getId(), repair);
        assertThat(fixed.getTaskStatus()).isEqualTo("DOING");
        assertThat(fixed.isAbnormal()).isFalse();
        assertThat(fixed.getNextStatus()).isEqualTo("DONE");

        var done = dispatchService.advance(nul.getId(), req("DONE", "DOING", "主管"));
        assertThat(done.task().getTaskStatus()).isEqualTo("DONE");

        List<DispatchStatusLogVO> h = dispatchService.history(nul.getId());
        DispatchStatusLogVO repairLog = h.stream().filter(x -> "REPAIR".equals(x.getChangeType())).findFirst().orElseThrow();
        assertThat(repairLog.getToStatus()).isEqualTo("DOING");
        assertThat(repairLog.getRawFromStatus()).isNull();
        assertThat(repairLog.getRemark()).isEqualTo("比对纸质派工单确认");

        // 合法状态不能走修复入口
        assertThatThrownBy(() -> dispatchService.repair(nul.getId(), repair))
                .isInstanceOf(BizException.class).hasMessageContaining("合法");
    }

    @Test
    void lifecycle_rules_unit_checks() {
        assertThat(DispatchLifecycle.canonical("todo")).isEqualTo("TODO");
        assertThat(DispatchLifecycle.canonical(" DO ")).isEqualTo("DOING");
        assertThat(DispatchLifecycle.canonical("已派")).isEqualTo("DOING");
        assertThat(DispatchLifecycle.canonical("完工")).isEqualTo("DONE");
        assertThat(DispatchLifecycle.canonical("")).isNull();
        assertThat(DispatchLifecycle.canonical("???")).isNull();
        assertThat(DispatchLifecycle.nextOf("TODO")).isEqualTo("DOING");
        assertThat(DispatchLifecycle.nextOf("DONE")).isNull();
        assertThat(DispatchLifecycle.isAdvance("TODO", "DOING")).isTrue();
        assertThat(DispatchLifecycle.isAdvance("TODO", "DONE")).isFalse();
    }

    private DispatchTask persistRaw(String no, String raw) {
        DispatchTask t = new DispatchTask();
        t.setTaskNo(no + System.nanoTime());
        t.setTaskType("FIX");
        t.setTaskStatus(raw);
        return taskRepository.save(t);
    }
}
