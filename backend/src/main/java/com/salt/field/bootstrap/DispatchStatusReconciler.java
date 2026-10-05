package com.salt.field.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.salt.field.service.DispatchService;

/**
 * 派工历史状态对账（启动执行一次）：库中的旧别名（DO / RUNNING / 已派 / 完工 等）
 * 归一化到最接近的合法状态并写 NORMALIZE 留痕，任务保持可用；
 * 空值 / 非法值不改写、不猜测——标记异常、冻结流转，等待人工核实修正，保留审计真相。
 * 幂等：已归一化的行不再命中别名条件，重复启动不会重复留痕。
 */
@Component
@Order(30)
@ConditionalOnProperty(name = "dispatch.reconcile-on-startup", havingValue = "true", matchIfMissing = true)
public class DispatchStatusReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DispatchStatusReconciler.class);

    private final DispatchService dispatchService;

    public DispatchStatusReconciler(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int affected = dispatchService.reconcileLegacyStatuses();
        if (affected > 0) {
            log.info("派工历史状态对账完成：{} 个任务的旧别名已归一化并留痕", affected);
        }
    }
}
