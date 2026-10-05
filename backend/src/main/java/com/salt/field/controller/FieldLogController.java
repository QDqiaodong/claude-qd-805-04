package com.salt.field.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.salt.field.common.ApiResponse;
import com.salt.field.dto.FieldLogCreateRequest;
import com.salt.field.dto.FieldLogVO;
import com.salt.field.dto.OverviewVO;
import com.salt.field.service.FieldLogService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class FieldLogController {

    private final FieldLogService fieldLogService;

    public FieldLogController(FieldLogService fieldLogService) {
        this.fieldLogService = fieldLogService;
    }

    /** 作业记录：{@code @Any} 多态关联，同一批数据里 targetType 可能是 POND / PLATFORM / EQUIPMENT */
    @GetMapping("/field-logs")
    public ApiResponse<List<FieldLogVO>> logs(@RequestParam(required = false) String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return ApiResponse.success(fieldLogService.logs());
        }
        return ApiResponse.success(fieldLogService.logsByTargetType(targetType));
    }

    /** 多态目标对象候选（池子 / 坨台 / 设备） */
    @GetMapping("/field-logs/options")
    public ApiResponse<List<Map<String, Object>>> options() {
        return ApiResponse.success(fieldLogService.targetOptions());
    }

    @PostMapping("/field-logs")
    public ApiResponse<FieldLogVO> createLog(@Valid @RequestBody FieldLogCreateRequest request) {
        return ApiResponse.success("作业记录已登记", fieldLogService.create(request));
    }

    @GetMapping("/overview")
    public ApiResponse<OverviewVO> overview() {
        return ApiResponse.success(fieldLogService.overview());
    }

    @GetMapping("/equipments")
    public ApiResponse<List<com.salt.field.domain.Equipment>> equipments() {
        return ApiResponse.success(fieldLogService.equipments());
    }
}
