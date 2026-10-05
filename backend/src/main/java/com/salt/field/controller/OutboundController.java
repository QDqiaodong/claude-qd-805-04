package com.salt.field.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.salt.field.common.ApiResponse;
import com.salt.field.dto.CorrectionVO;
import com.salt.field.dto.OutboundRequest;
import com.salt.field.dto.OutboundReversalRequest;
import com.salt.field.dto.OutboundReviewRequest;
import com.salt.field.dto.OutboundVO;
import com.salt.field.dto.PlateCorrectionRequest;
import com.salt.field.service.OutboundService;

import jakarta.validation.Valid;

/**
 * 出仓「登记预占 — 复核确认 — 冲正重记」接口。
 *
 * <p>库存口径（页面同步说明）：登记只预占（冻结）吨数，复核确认才实扣账面；
 * 待复核单可作废释放预占；已确认单不删除，仅车牌错误可直改，吨数/坨台/客户错误走冲正重记。
 */
@RestController
@RequestMapping("/api/outbounds")
public class OutboundController {

    private final OutboundService outboundService;

    public OutboundController(OutboundService outboundService) {
        this.outboundService = outboundService;
    }

    /** 登记出仓（待复核，预占吨数，不扣账面） */
    @PostMapping("/platform/{id}")
    public ApiResponse<OutboundVO> register(@PathVariable Long id,
                                            @Valid @RequestBody OutboundRequest request) {
        return ApiResponse.success("出仓已登记，待复核（已预占）", outboundService.register(id, request));
    }

    /** 全部出仓流水；status=PENDING 即复核队列 */
    @GetMapping
    public ApiResponse<List<OutboundVO>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(outboundService.allOutbounds(status));
    }

    /** 单坨台账（全部状态） */
    @GetMapping("/platform/{id}")
    public ApiResponse<List<OutboundVO>> byPlatform(@PathVariable Long id) {
        return ApiResponse.success(outboundService.outbounds(id));
    }

    /** 复核确认：预占转实扣（账面库存减少） */
    @PostMapping("/{id}/confirm")
    public ApiResponse<OutboundVO> confirm(@PathVariable Long id,
                                           @Valid @RequestBody OutboundReviewRequest request) {
        return ApiResponse.success("复核确认成功，库存已实扣", outboundService.confirm(id, request.getOperator()));
    }

    /** 复核作废：释放预占（账面从未扣减） */
    @PostMapping("/{id}/reject")
    public ApiResponse<OutboundVO> reject(@PathVariable Long id,
                                          @Valid @RequestBody OutboundReviewRequest request) {
        return ApiResponse.success("已作废，预占已释放",
                outboundService.reject(id, request.getOperator(), request.getReason()));
    }

    /** 仅车牌错误直接更正（坨台 / 吨数 / 客户不变，不动库存） */
    @PostMapping("/{id}/correct-plate")
    public ApiResponse<OutboundVO> correctPlate(@PathVariable Long id,
                                                @Valid @RequestBody PlateCorrectionRequest request) {
        return ApiResponse.success("车牌已更正", outboundService.correctPlate(id, request));
    }

    /** 已确认单冲正后按新单重记（库存先原子恢复、再按新单扣减，禁止重复冲正） */
    @PostMapping("/{id}/reverse-rerecord")
    public ApiResponse<OutboundVO> reverseRerecord(@PathVariable Long id,
                                                   @Valid @RequestBody OutboundReversalRequest request) {
        return ApiResponse.success("已冲正并重记", outboundService.reverseAndReRecord(id, request));
    }

    /** 一张单的更正痕迹（原值 / 新值 / 操作人 / 时间） */
    @GetMapping("/{id}/corrections")
    public ApiResponse<List<CorrectionVO>> corrections(@PathVariable Long id) {
        return ApiResponse.success(outboundService.corrections(id));
    }
}
