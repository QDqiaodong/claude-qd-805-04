package com.salt.field.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.salt.field.common.ApiResponse;
import com.salt.field.dto.DispatchAdvanceResult;
import com.salt.field.dto.DispatchRepairRequest;
import com.salt.field.dto.DispatchStatusLogVO;
import com.salt.field.dto.DispatchStatusRequest;
import com.salt.field.dto.DispatchTaskVO;
import com.salt.field.service.DispatchService;

import jakarta.validation.Valid;

/**
 * 场务调度任务：派工列表、详情（含流转历史）、下达、生命周期推进、历史异常修正。
 * 状态机 / 并发前置条件 / 留痕规则见 DispatchService。
 */
@RestController
@RequestMapping("/api/dispatch")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    /** 派工列表（异常标记、唯一合法下一步随每行返回，前端只渲染可执行入口） */
    @GetMapping
    public ApiResponse<List<DispatchTaskVO>> list() {
        return ApiResponse.success(dispatchService.tasks());
    }

    /** 详情：任务信息 + 合法下一步 + 异常提示 + 完整状态流转历史 */
    @GetMapping("/{id}")
    public ApiResponse<DispatchTaskVO> detail(@PathVariable Long id) {
        return ApiResponse.success(dispatchService.detail(id));
    }

    /** 某任务的状态流转历史（前态/后态/原始值/操作人/时间） */
    @GetMapping("/{id}/history")
    public ApiResponse<List<DispatchStatusLogVO>> history(@PathVariable Long id) {
        return ApiResponse.success(dispatchService.history(id));
    }

    /** 下达派工任务（一律以 TODO 待派进入生命周期） */
    @PostMapping
    public ApiResponse<DispatchTaskVO> create(@RequestBody Map<String, Object> body) {
        String taskType = str(body.get("taskType"), "FIX");
        String targetType = str(body.get("targetType"), "POND");
        Long targetId = body.get("targetId") == null ? null : Long.valueOf(body.get("targetId").toString());
        String crew = str(body.get("crew"), "一班");
        LocalDate planDate = body.get("planDate") == null ? LocalDate.now()
                : LocalDate.parse(body.get("planDate").toString());
        String remark = str(body.get("remark"), "");
        String operator = str(body.get("operator"), "调度员");
        return ApiResponse.success("调度任务已下达",
                dispatchService.create(taskType, targetType, targetId, crew, planDate, remark, operator));
    }

    /**
     * 生命周期推进：只接受合法的下一步；重复提交幂等；
     * expectedStatus 与最新状态不符返回 409（已完成结果不被覆盖）。
     */
    @PutMapping("/{id}/status")
    public ApiResponse<DispatchTaskVO> advance(@PathVariable Long id,
                                               @Valid @RequestBody DispatchStatusRequest request) {
        DispatchAdvanceResult result = dispatchService.advance(id, request);
        DispatchTaskVO vo = result.task();
        // 重复提交同一目标：幂等无变化、无新留痕
        String message = result.idempotent()
                ? "任务当前已是「" + vo.getTaskStatusName() + "」，状态保持不变（幂等，未重复留痕）"
                : "调度任务状态已推进为「" + vo.getTaskStatusName() + "」";
        return ApiResponse.success(message, vo);
    }

    /** 历史异常（空值 / 非法状态）人工核实修正：原值留痕，只允许改入合法状态 */
    @PostMapping("/{id}/repair")
    public ApiResponse<DispatchTaskVO> repair(@PathVariable Long id,
                                              @Valid @RequestBody DispatchRepairRequest request) {
        return ApiResponse.success("异常状态已修正并留痕，任务可继续流转",
                dispatchService.repair(id, request));
    }

    private String str(Object v, String def) {
        return v == null ? def : v.toString();
    }
}
