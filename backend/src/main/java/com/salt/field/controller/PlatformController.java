package com.salt.field.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.salt.field.common.ApiResponse;
import com.salt.field.dto.PlatformStockStat;
import com.salt.field.dto.PlatformVO;
import com.salt.field.service.PlatformService;

@RestController
@RequestMapping("/api/platforms")
public class PlatformController {

    private final PlatformService platformService;

    public PlatformController(PlatformService platformService) {
        this.platformService = platformService;
    }

    /** 坨台卡片（3D 立方体）：库存 / 预占中 / 可用 / 累计实发 */
    @GetMapping
    public ApiResponse<List<PlatformVO>> list() {
        return ApiResponse.success(platformService.list());
    }

    /** 坨台库存、预占与发运汇总（JPQL 聚合） */
    @GetMapping("/stock-stats")
    public ApiResponse<List<PlatformStockStat>> stockStats() {
        return ApiResponse.success(platformService.stockStats());
    }
}
