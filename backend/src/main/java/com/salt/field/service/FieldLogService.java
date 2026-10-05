package com.salt.field.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salt.field.common.BizException;
import com.salt.field.common.FieldDict;
import com.salt.field.domain.Equipment;
import com.salt.field.domain.FieldLog;
import com.salt.field.domain.HarvestTask;
import com.salt.field.domain.Pond;
import com.salt.field.domain.SaltPlatform;
import com.salt.field.domain.SaltTarget;
import com.salt.field.dto.FieldLogCreateRequest;
import com.salt.field.dto.FieldLogVO;
import com.salt.field.dto.OverviewVO;
import com.salt.field.repository.EquipmentRepository;
import com.salt.field.repository.FieldLogRepository;
import com.salt.field.repository.HarvestTaskRepository;
import com.salt.field.repository.PondAreaRepository;
import com.salt.field.repository.PondRepository;
import com.salt.field.repository.SaltPlatformRepository;
import com.salt.field.repository.WeatherLogRepository;

/**
 * 作业记录与场务调度：集中处理 {@code @Any} 多态关联的读写。
 */
@Service
public class FieldLogService {

    private final FieldLogRepository logRepository;
    private final PondRepository pondRepository;
    private final SaltPlatformRepository platformRepository;
    private final EquipmentRepository equipmentRepository;
    private final PondAreaRepository areaRepository;
    private final HarvestTaskRepository harvestRepository;
    private final WeatherLogRepository weatherRepository;

    public FieldLogService(FieldLogRepository logRepository,
                           PondRepository pondRepository,
                           SaltPlatformRepository platformRepository,
                           EquipmentRepository equipmentRepository,
                           PondAreaRepository areaRepository,
                           HarvestTaskRepository harvestRepository,
                           WeatherLogRepository weatherRepository) {
        this.logRepository = logRepository;
        this.pondRepository = pondRepository;
        this.platformRepository = platformRepository;
        this.equipmentRepository = equipmentRepository;
        this.areaRepository = areaRepository;
        this.harvestRepository = harvestRepository;
        this.weatherRepository = weatherRepository;
    }

    // ---------------- @Any：作业记录 ----------------

    /** 全部作业记录，返回时把多态关联解析成「类型 + 名称」 */
    public List<FieldLogVO> logs() {
        List<FieldLogVO> list = new ArrayList<>();
        logRepository.findTop20ByOrderByLogTimeDesc().forEach(l -> list.add(FieldLogVO.of(l)));
        return list;
    }

    /** 按多态目标类型过滤（POND / PLATFORM / EQUIPMENT） */
    public List<FieldLogVO> logsByTargetType(String targetType) {
        if (!FieldDict.isValidTargetType(targetType)) {
            throw new BizException("关联对象类型取值有误：" + targetType + "（应为 POND/PLATFORM/EQUIPMENT）");
        }
        List<FieldLogVO> list = new ArrayList<>();
        logRepository.findTop20ByOrderByLogTimeDesc().stream()
                .filter(l -> l.getTarget() != null && targetType.equals(l.getTarget().targetType()))
                .forEach(l -> list.add(FieldLogVO.of(l)));
        return list;
    }

    /**
     * 写一条作业记录：target 由 targetType + targetId 解析成真正的实体，交给 {@code @Any} 落库。
     */
    @Transactional
    public FieldLogVO create(FieldLogCreateRequest request) {
        SaltTarget target = resolveTarget(request.getTargetType(), request.getTargetId());
        FieldLog log = new FieldLog();
        log.setLogType(request.getLogType());
        log.setContent(request.getContent());
        log.setOperator(request.getOperator() == null ? "值班员" : request.getOperator());
        log.setLogTime(request.getLogTime() == null ? LocalDateTime.now() : request.getLogTime());
        log.setTarget(target);
        return FieldLogVO.of(logRepository.save(log));
    }

    @Transactional
    public FieldLog write(String logType, SaltTarget target, String content, String operator) {
        FieldLog log = new FieldLog();
        log.setLogType(logType);
        log.setContent(content);
        log.setOperator(operator == null ? "系统" : operator);
        log.setLogTime(LocalDateTime.now());
        log.setTarget(target);
        return logRepository.save(log);
    }

    /** 依据类型标识解析多态目标实体 */
    public SaltTarget resolveTarget(String targetType, Long targetId) {
        if (targetId == null) {
            throw new BizException("关联对象主键不能为空");
        }
        if ("POND".equals(targetType)) {
            return pondRepository.findById(targetId)
                    .orElseThrow(() -> new BizException("池子不存在：id=" + targetId));
        }
        if ("PLATFORM".equals(targetType)) {
            return platformRepository.findById(targetId)
                    .orElseThrow(() -> new BizException("坨台不存在：id=" + targetId));
        }
        if ("EQUIPMENT".equals(targetType)) {
            return equipmentRepository.findById(targetId)
                    .orElseThrow(() -> new BizException("设备不存在：id=" + targetId));
        }
        throw new BizException("关联对象类型取值有误：" + targetType + "（应为 POND/PLATFORM/EQUIPMENT）");
    }

    /** 三种多态目标对象清单，供前端下拉选择 */
    public List<Map<String, Object>> targetOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        pondRepository.findAll().forEach(p -> options.add(option("POND", p.getId(), p.getPondNo() + " " + p.getName())));
        platformRepository.findAll().forEach(p -> options.add(option("PLATFORM", p.getId(), p.getPlatformNo() + " " + p.getName())));
        equipmentRepository.findAll().forEach(e -> options.add(option("EQUIPMENT", e.getId(), e.getEquipNo() + " " + e.getName())));
        return options;
    }

    private Map<String, Object> option(String type, Long id, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("targetType", type);
        m.put("targetTypeName", FieldDict.targetTypeName(type));
        m.put("targetId", id);
        m.put("targetName", label);
        return m;
    }

    // ---------------- 概览 ----------------

    public OverviewVO overview() {
        OverviewVO v = new OverviewVO();
        v.setAreaCount(areaRepository.count());
        v.setPondCount(pondRepository.count());
        v.setPlatformCount(platformRepository.count());
        BigDecimal stock = platformRepository.sumStock();
        v.setStockTons(stock == null ? BigDecimal.ZERO : stock);
        LocalDate today = LocalDate.now();
        BigDecimal done = harvestRepository.sumDoneTons(today);
        v.setTodayTons(done == null ? BigDecimal.ZERO : done);
        v.setPlanTons(FieldDict.DAILY_PLAN_TONS);
        List<com.salt.field.dto.DailyWeatherStat> todayStats = weatherRepository.statDaily(today);
        BigDecimal evap = BigDecimal.ZERO;
        BigDecimal rain = BigDecimal.ZERO;
        if (!todayStats.isEmpty()) {
            com.salt.field.dto.DailyWeatherStat s = todayStats.get(0);
            evap = s.evaporationTotal() == null ? BigDecimal.ZERO : s.evaporationTotal();
            rain = s.rainfallTotal() == null ? BigDecimal.ZERO : s.rainfallTotal();
        }
        v.setTodayEvaporation(evap);
        v.setTodayRainfall(rain);
        v.setWaitingPondCount(pondRepository.findByPondStatus("WAIT").stream().count());
        v.setRepairingPondCount(pondRepository.findByPondStatus("REPAIR").stream().count());
        List<String> teams = new ArrayList<>();
        harvestRepository.statByTeam(today).forEach(s -> teams.add(s.teamName()));
        v.setTeams(teams);
        return v;
    }

    /** 设备清单（设备也是多态目标之一） */
    public List<Equipment> equipments() {
        return equipmentRepository.findAll();
    }

    /** 作业单清单（按池子） */
    public List<HarvestTask> harvestByPond(Long pondId) {
        return harvestRepository.findByPond(pondId);
    }

    /** 池子与坨台的简要信息，前端表单下拉用 */
    public List<Pond> ponds() {
        return pondRepository.findAll();
    }

    public List<SaltPlatform> platforms() {
        return platformRepository.findAll();
    }
}
