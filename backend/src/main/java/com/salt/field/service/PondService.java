package com.salt.field.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salt.field.common.BizException;
import com.salt.field.common.FieldDict;
import com.salt.field.domain.Pond;
import com.salt.field.domain.PondArea;
import com.salt.field.dto.AreaBrineStat;
import com.salt.field.dto.AreaTreeVO;
import com.salt.field.dto.BrineRegisterRequest;
import com.salt.field.dto.PondVO;
import com.salt.field.repository.PondAreaRepository;
import com.salt.field.repository.PondRepository;

@Service
public class PondService {

    private final PondRepository pondRepository;
    private final PondAreaRepository areaRepository;
    private final FieldLogService fieldLogService;
    private final RainStopService rainStopService;

    public PondService(PondRepository pondRepository,
                       PondAreaRepository areaRepository,
                       FieldLogService fieldLogService,
                       RainStopService rainStopService) {
        this.pondRepository = pondRepository;
        this.areaRepository = areaRepository;
        this.fieldLogService = fieldLogService;
        this.rainStopService = rainStopService;
    }

    /** 左侧池区树：池区 → 池号（批量装配雨后停工状态） */
    public List<AreaTreeVO> tree() {
        List<Pond> all = pondRepository.findAll();
        var rainStates = rainStopService.states(all.stream().map(Pond::getId).toList());
        List<AreaTreeVO> result = new ArrayList<>();
        for (PondArea area : areaRepository.findAllByOrderByBandIndexAsc()) {
            AreaTreeVO node = AreaTreeVO.of(area.getId(), area.getCode(), area.getName(), area.getAreaType(),
                    area.getAreaMu(), area.getTideGate(), area.getOwnerName(), area.getBandIndex());
            for (Pond pond : pondRepository.findByAreaIdOrderByGridRowAscGridColAsc(area.getId())) {
                PondVO vo = toVO(pond);
                rainStopService.enrichBatch(List.of(vo), rainStates);
                node.getPonds().add(vo);
            }
            result.add(node);
        }
        return result;
    }

    /** 顶部 chips 条用的扁平池号列表（批量装配雨后停工状态） */
    public List<PondVO> flatPonds() {
        List<Pond> ponds = pondRepository.findAll();
        var rainStates = rainStopService.states(ponds.stream().map(Pond::getId).toList());
        List<PondVO> list = new ArrayList<>();
        ponds.forEach(p -> list.add(toVO(p)));
        rainStopService.enrichBatch(list, rainStates);
        list.sort((a, b) -> a.getPondNo().compareTo(b.getPondNo()));
        return list;
    }

    public List<AreaBrineStat> areaBrineStats() {
        return areaRepository.statBrineByArea();
    }

    public PondVO detail(Long pondId) {
        return rainStopService.enrich(toVO(require(pondId)), require(pondId));
    }

    /**
     * 登记卤度 / 卤水深度 / 池板状况。
     * 规则：池板状况与卤度共同决定池子作业状态（需修 → 修池；≥25°Bé → 待收；≥20°Bé → 结晶；否则晒卤）。
     * 注意：测卤不改变雨后窗口——窗口只由有效降雨重新起算。
     */
    @Transactional
    public PondVO registerBrine(Long pondId, BrineRegisterRequest request) {
        Pond pond = require(pondId);
        BigDecimal degree = request.getBrineDegree();
        BigDecimal depth = request.getBrineDepth();
        if (degree == null || degree.compareTo(BigDecimal.ZERO) < 0 || degree.compareTo(BigDecimal.valueOf(60)) > 0) {
            throw new BizException("卤度需在 0 ~ 60 °Bé 之间，当前录入 " + degree);
        }
        if (depth == null || depth.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("卤水深度不能为负数，当前录入 " + depth);
        }
        String boardStatus = request.getBoardStatus() == null ? pond.getBoardStatus() : request.getBoardStatus();
        if (!FieldDict.isValidBoardStatus(boardStatus)) {
            throw new BizException("池板状况取值有误：" + boardStatus + "（应为 GOOD/LEAK/REPAIR）");
        }

        pond.setBrineDegree(degree);
        pond.setBrineDepth(depth);
        pond.setBoardStatus(boardStatus);
        pond.setPondStatus(resolveStatus(boardStatus, degree));
        Pond saved = pondRepository.save(pond);

        // 写一条作业记录，@Any 多态关联指向「池子」这个对象
        fieldLogService.write("BRINE", saved,
                "测卤 " + degree + "°Bé，卤水深 " + depth + "cm，池板" + FieldDict.boardStatusName(boardStatus),
                request.getOperator() == null ? "测卤员" : request.getOperator());
        return rainStopService.enrich(toVO(saved), saved);
    }

    private String resolveStatus(String boardStatus, BigDecimal degree) {
        if ("REPAIR".equals(boardStatus)) {
            return "REPAIR";
        }
        if (degree.compareTo(FieldDict.HARVEST_MIN_BRINE) >= 0) {
            return "WAIT";
        }
        if (degree.compareTo(FieldDict.CRYST_MIN_BRINE) >= 0) {
            return "CRYST";
        }
        return "SUN";
    }

    public Pond require(Long pondId) {
        return pondRepository.findById(pondId)
                .orElseThrow(() -> new BizException("池子不存在：id=" + pondId));
    }

    public static PondVO toVO(Pond pond) {
        PondArea area = pond.getArea();
        return PondVO.of(pond.getId(), pond.getPondNo(), pond.getName(),
                area == null ? null : area.getId(),
                area == null ? "" : area.getName(),
                area == null ? "" : area.getAreaType(),
                pond.getAreaMu(), pond.getBoardStatus(), pond.getBrineDegree(), pond.getBrineDepth(),
                pond.getPondStatus(), pond.getGridRow(), pond.getGridCol());
    }
}
