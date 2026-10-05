package com.salt.field.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.salt.field.domain.SaltPlatform;
import com.salt.field.dto.PlatformStockStat;
import com.salt.field.dto.PlatformVO;
import com.salt.field.repository.SaltPlatformRepository;

/**
 * 坨台库存台账视图。
 *
 * <p>库存口径（「登记预占、确认实扣」，页面同步公示）：
 * <ul>
 *   <li><b>账面库存</b> {@code stockTons}：仅出仓单复核确认（CONFIRMED）后才实扣减少；</li>
 *   <li><b>预占中</b> {@code reservedTons}：待复核（PENDING）单冻结量，登记即占用、不动账面；</li>
 *   <li><b>可用库存</b> {@code availableTons} = 账面库存 − 预占中，新登记的可登记上限；</li>
 *   <li><b>累计实发</b> {@code outTons}：只统计 CONFIRMED，REVERSED 已冲回、不计入。</li>
 * </ul>
 */
@Service
public class PlatformService {

    private final SaltPlatformRepository platformRepository;

    public PlatformService(SaltPlatformRepository platformRepository) {
        this.platformRepository = platformRepository;
    }

    public List<PlatformVO> list() {
        Map<Long, PlatformStockStat> statMap = new LinkedHashMap<>();
        for (PlatformStockStat s : platformRepository.statStockAndOutbound()) {
            statMap.put(s.platformId(), s);
        }
        List<PlatformVO> list = new java.util.ArrayList<>();
        for (SaltPlatform p : platformRepository.findAll()) {
            PlatformStockStat s = statMap.get(p.getId());
            list.add(toVO(p,
                    s == null ? null : s.outTons(),
                    s == null ? null : s.reservedTons(),
                    s == null ? 0L : s.pendingCount()));
        }
        list.sort((a, b) -> a.getPlatformNo().compareTo(b.getPlatformNo()));
        return list;
    }

    public List<PlatformStockStat> stockStats() {
        return platformRepository.statStockAndOutbound();
    }

    public PlatformVO toVO(SaltPlatform p, java.math.BigDecimal outTons,
                           java.math.BigDecimal reservedTons, Long pendingCount) {
        return PlatformVO.of(p.getId(), p.getPlatformNo(), p.getName(), p.getSaltType(),
                p.getStockTons(), p.getCapacityTons(), p.getMoisture(), p.getPileShape(), p.getLocation(),
                outTons, reservedTons, pendingCount);
    }
}
