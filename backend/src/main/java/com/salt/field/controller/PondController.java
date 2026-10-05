package com.salt.field.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.salt.field.common.ApiResponse;
import com.salt.field.domain.Pond;
import com.salt.field.dto.AreaBrineStat;
import com.salt.field.dto.AreaTreeVO;
import com.salt.field.dto.BrineRegisterRequest;
import com.salt.field.dto.PondVO;
import com.salt.field.service.FieldLogService;
import com.salt.field.service.PondService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ponds")
public class PondController {

    private final PondService pondService;
    private final FieldLogService fieldLogService;

    public PondController(PondService pondService, FieldLogService fieldLogService) {
        this.pondService = pondService;
        this.fieldLogService = fieldLogService;
    }

    /** 左侧池区树（池区 → 池号） */
    @GetMapping("/tree")
    public ApiResponse<List<AreaTreeVO>> tree() {
        return ApiResponse.success(pondService.tree());
    }

    /** 顶部池号 chips 条 */
    @GetMapping
    public ApiResponse<List<PondVO>> list() {
        return ApiResponse.success(pondService.flatPonds());
    }

    /** 按池区统计卤度均值（JPQL 聚合） */
    @GetMapping("/area-stats")
    public ApiResponse<List<AreaBrineStat>> areaStats() {
        return ApiResponse.success(pondService.areaBrineStats());
    }

    @GetMapping("/{id}")
    public ApiResponse<PondVO> detail(@PathVariable Long id) {
        return ApiResponse.success(pondService.detail(id));
    }

    /** 池子的作业记录（@Any 多态关联回显） */
    @GetMapping("/{id}/logs")
    public ApiResponse<List<Map<String, Object>>> pondLogs(@PathVariable Long id) {
        Pond pond = pondService.require(id);
        return ApiResponse.success(fieldLogService.logs().stream()
                .filter(l -> pond.getId().equals(l.getTargetId()) && "POND".equals(l.getTargetType()))
                .map(l -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", l.getId());
                    m.put("logType", l.getLogType());
                    m.put("logTypeName", l.getLogTypeName());
                    m.put("content", l.getContent());
                    m.put("operator", l.getOperator());
                    m.put("logTime", l.getLogTime());
                    m.put("targetType", l.getTargetType());
                    m.put("targetName", l.getTargetName());
                    return m;
                }).toList());
    }

    /** 登记卤度、卤水深度、池板状况 */
    @PostMapping("/{id}/brine")
    public ApiResponse<PondVO> registerBrine(@PathVariable Long id,
                                             @Valid @RequestBody BrineRegisterRequest request) {
        return ApiResponse.success("卤度已登记", pondService.registerBrine(id, request));
    }
}
