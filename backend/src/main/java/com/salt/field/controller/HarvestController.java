package com.salt.field.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.salt.field.common.ApiResponse;
import com.salt.field.dto.HarvestCreateRequest;
import com.salt.field.dto.HarvestResumeRequest;
import com.salt.field.dto.HarvestStatusRequest;
import com.salt.field.dto.HarvestVO;
import com.salt.field.service.HarvestService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/harvest")
public class HarvestController {

    private final HarvestService harvestService;

    public HarvestController(HarvestService harvestService) {
        this.harvestService = harvestService;
    }

    /** 班次条看板：当日作业单 + 班组汇总 + 进度环 */
    @GetMapping("/board")
    public ApiResponse<Map<String, Object>> board(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(harvestService.board(date));
    }

    /** 下达收盐作业（卤度 / 雨后 24h / 池板需修 三条规则校验） */
    @PostMapping
    public ApiResponse<HarvestVO> create(@Valid @RequestBody HarvestCreateRequest request) {
        return ApiResponse.success("收盐作业已下达", harvestService.create(request));
    }

    /** 改作业状态（雨情停工单须先复工确认，RAIN_STOP 不能人工直接改入/改出） */
    @PutMapping("/{id}/status")
    public ApiResponse<HarvestVO> updateStatus(@PathVariable Long id,
                                               @Valid @RequestBody HarvestStatusRequest request) {
        return ApiResponse.success("作业状态已更新", harvestService.updateStatus(id, request.getTaskStatus()));
    }

    /**
     * 雨后复工确认。须同时满足：① 最近一次有效降雨已满 24 小时；
     * ② 最新卤度 ≥ 25°Bé；③ 池板非「需修」。任一不满足时返回逐条拦截项。
     * 停工期间再次降雨的，窗口按最新降雨重新起算。
     */
    @PostMapping("/{id}/resume")
    public ApiResponse<HarvestVO> resume(@PathVariable Long id,
                                         @RequestBody(required = false) HarvestResumeRequest request) {
        String operator = request == null ? null : request.getOperator();
        return ApiResponse.success("雨后复工已确认", harvestService.resume(id, operator));
    }
}
