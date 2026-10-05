package com.salt.field.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.salt.field.service.RainStopService;

/**
 * 启动后按最新雨情对账：池内已有窗口内有效降雨、但作业仍处于待作业/作业中的，
 * 补做「雨情停工」；已停工的单子不会被自动复工。
 * 保证重启（含重新录入的降雨）后，班次条与池子状态、时间、原因一致。
 */
@Component
@Order(20)
public class RainStopReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RainStopReconciler.class);

    private final RainStopService rainStopService;

    public RainStopReconciler(RainStopService rainStopService) {
        this.rainStopService = rainStopService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int affected = rainStopService.reconcileOnStartup();
        if (affected > 0) {
            log.info("雨后停工对账完成：{} 单未完成作业已按窗口内最新降雨转为雨情停工", affected);
        }
    }
}
