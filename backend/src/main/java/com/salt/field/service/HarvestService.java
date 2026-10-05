package com.salt.field.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salt.field.common.BizException;
import com.salt.field.common.FieldDict;
import com.salt.field.domain.HarvestTask;
import com.salt.field.domain.Pond;
import com.salt.field.domain.SaltPlatform;
import com.salt.field.domain.WeatherLog;
import com.salt.field.dto.HarvestCreateRequest;
import com.salt.field.dto.HarvestVO;
import com.salt.field.dto.TeamHarvestStat;
import com.salt.field.repository.HarvestTaskRepository;
import com.salt.field.repository.PondRepository;
import com.salt.field.repository.SaltPlatformRepository;

@Service
public class HarvestService {

    private final HarvestTaskRepository harvestRepository;
    private final PondRepository pondRepository;
    private final SaltPlatformRepository platformRepository;
    private final RainStopService rainStopService;
    private final FieldLogService fieldLogService;

    public HarvestService(HarvestTaskRepository harvestRepository,
                          PondRepository pondRepository,
                          SaltPlatformRepository platformRepository,
                          RainStopService rainStopService,
                          FieldLogService fieldLogService) {
        this.harvestRepository = harvestRepository;
        this.pondRepository = pondRepository;
        this.platformRepository = platformRepository;
        this.rainStopService = rainStopService;
        this.fieldLogService = fieldLogService;
    }

    /** 某日的作业班次条 + 班组汇总 + 进度环数据 */
    public Map<String, Object> board(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<HarvestTask> tasks = harvestRepository.findByDate(day);
        List<HarvestVO> rows = new ArrayList<>();
        for (HarvestTask t : tasks) {
            rows.add(toVO(t));
        }
        BigDecimal doneTons = harvestRepository.sumDoneTons(day);
        BigDecimal plannedTons = rows.stream()
                .filter(r -> !"CANCELED".equals(r.getTaskStatus()))
                .map(HarvestVO::getTons)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long stoppedCount = rows.stream().filter(r -> "RAIN_STOP".equals(r.getTaskStatus())).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workDate", day);
        result.put("tasks", rows);
        result.put("teamStats", harvestRepository.statByTeam(day));
        result.put("doneTons", doneTons == null ? BigDecimal.ZERO : doneTons);
        result.put("planTons", FieldDict.DAILY_PLAN_TONS);
        result.put("scheduledTons", plannedTons);
        result.put("rainStoppedCount", stoppedCount);
        return result;
    }

    /**
     * 下达收盐作业。持池行悲观锁后按顺序校验，命中即抛 BizException：
     * ① 池板状况为「需修」不允许排作业；② 卤度未达 25°Bé 不允许；③ 雨后 24 小时内不允许。
     * 锁保证与同时发生的降雨登记串行化。
     */
    @Transactional
    public HarvestVO create(HarvestCreateRequest request) {
        if (!pondRepository.existsById(request.getPondId())) {
            throw new BizException("池子不存在：id=" + request.getPondId());
        }
        Pond pond = pondRepository.lockById(request.getPondId());
        SaltPlatform platform = platformRepository.findById(request.getPlatformId())
                .orElseThrow(() -> new BizException("坨台不存在：id=" + request.getPlatformId()));

        // 规则①：池板需修
        if ("REPAIR".equals(pond.getBoardStatus())) {
            throw new BizException("池板状况为「需修」，不允许下达收盐作业：池号 " + pond.getPondNo());
        }

        // 规则②：卤度门槛
        BigDecimal brine = pond.getBrineDegree() == null ? BigDecimal.ZERO : pond.getBrineDegree();
        if (brine.compareTo(FieldDict.HARVEST_MIN_BRINE) < 0) {
            throw new BizException("卤度未达标，不允许收盐：池号 " + pond.getPondNo()
                    + " 当前 " + brine.stripTrailingZeros().toPlainString() + "°Bé，门槛 "
                    + FieldDict.HARVEST_MIN_BRINE + "°Bé");
        }

        // 规则③：雨后 24 小时（锁内复核最新雨情，拦截「排作业与降雨同时发生」）
        WeatherLog rain = rainStopService.activeRain(pond.getId());
        if (rain != null) {
            throw new BizException("雨后 " + FieldDict.RAIN_BLOCK_HOURS + " 小时内不允许收盐：池号 " + pond.getPondNo()
                    + " 于 " + rain.getRecordedAt().format(RainStopService.DT) + " 记录降雨 "
                    + rain.getRainfall().stripTrailingZeros().toPlainString() + "mm，最早可复工 "
                    + rainStopService.resumeAt(rain).format(RainStopService.DT));
        }

        BigDecimal tons = request.getTons();
        if (tons == null || tons.signum() <= 0) {
            throw new BizException("收盐吨数必须大于 0");
        }
        if (request.getDurationHours() == null || request.getDurationHours().signum() <= 0) {
            throw new BizException("作业时长必须大于 0 小时");
        }

        HarvestTask task = new HarvestTask();
        task.setTaskNo(nextTaskNo());
        task.setPondId(pond.getId());
        task.setPlatformId(platform.getId());
        task.setWorkDate(request.getWorkDate() == null ? LocalDate.now() : request.getWorkDate());
        task.setTeamName(request.getTeamName());
        task.setStartHour(request.getStartHour());
        task.setDurationHours(request.getDurationHours());
        task.setTons(tons);
        task.setWorkMode(request.getWorkMode() == null ? "MACHINE" : request.getWorkMode());
        task.setTaskStatus("PLANNED");
        task.setOperator(request.getOperator() == null ? "调度员" : request.getOperator());
        HarvestTask saved = harvestRepository.save(task);

        fieldLogService.write("HARVEST", pond,
                "下达收盐作业 " + saved.getTaskNo() + "，" + saved.getTeamName() + " 计划收盐 "
                        + tons.stripTrailingZeros().toPlainString() + " 吨 → 坨台 " + platform.getPlatformNo(),
                saved.getOperator());
        return toVO(saved);
    }

    /**
     * 人工改作业状态（PLANNED/WORKING/DONE/CANCELED）。
     * <ul>
     *   <li>RAIN_STOP 只能经「复工确认」退出，直接改入/改出一律拦截；</li>
     *   <li>持池行锁后复核雨后窗口：窗口内不允许开始（WORKING），更不允许完成入账（DONE），
     *       杜绝「绕过雨后窗口完成入账」；</li>
     *   <li>置为 DONE 时把收盐量计入坨台库存。</li>
     * </ul>
     */
    @Transactional
    public HarvestVO updateStatus(Long taskId, String status) {
        if (!FieldDict.isManualAssignableTaskStatus(status)) {
            throw new BizException("作业状态取值有误：" + status
                    + "（人工仅可置 PLANNED/WORKING/DONE/CANCELED；雨情停工由系统自动设置，需走复工确认）");
        }
        HarvestTask task = harvestRepository.findById(taskId)
                .orElseThrow(() -> new BizException("作业单不存在：id=" + taskId));
        // 统一加锁顺序：先池行、再作业单行。锁上的读是当前读，
        // 能看到锁等待期间降雨登记事务刚提交的 RAIN_STOP 与最新雨情
        Pond pond = pondRepository.lockById(task.getPondId());
        task = harvestRepository.lockById(taskId);
        if ("RAIN_STOP".equals(task.getTaskStatus())) {
            throw new BizException("作业单 " + task.getTaskNo()
                    + " 处于「雨情停工」，须先由调度员确认复工，不能直接改为「"
                    + FieldDict.taskStatusName(status) + "」");
        }
        WeatherLog rain = rainStopService.activeRain(task.getPondId());
        if (rain != null && ("WORKING".equals(status) || "DONE".equals(status))) {
            throw new BizException("雨后 " + FieldDict.RAIN_BLOCK_HOURS + " 小时窗口内禁止"
                    + ("DONE".equals(status) ? "完成入账" : "开始作业") + "：池号 " + pond.getPondNo()
                    + " 于 " + rain.getRecordedAt().format(RainStopService.DT) + " 降雨 "
                    + rain.getRainfall().stripTrailingZeros().toPlainString() + "mm，最早 "
                    + rainStopService.resumeAt(rain).format(RainStopService.DT) + " 可复工");
        }

        String old = task.getTaskStatus();
        task.setTaskStatus(status);
        if ("DONE".equals(status) && !"DONE".equals(old)) {
            SaltPlatform platform = platformRepository.findById(task.getPlatformId()).orElse(null);
            if (platform != null) {
                BigDecimal stock = platform.getStockTons() == null ? BigDecimal.ZERO : platform.getStockTons();
                BigDecimal tons = task.getTons() == null ? BigDecimal.ZERO : task.getTons();
                if (platform.getCapacityTons() != null
                        && stock.add(tons).compareTo(platform.getCapacityTons()) > 0) {
                    throw new BizException("坨台 " + platform.getPlatformNo() + " 容量不足：现有 "
                            + stock.stripTrailingZeros().toPlainString() + " 吨，容量 "
                            + platform.getCapacityTons().stripTrailingZeros().toPlainString() + " 吨");
                }
                platform.setStockTons(stock.add(tons));
                platformRepository.save(platform);
                // @Any 多态关联指向「坨台」
                fieldLogService.write("HARVEST", platform,
                        "作业单 " + task.getTaskNo() + " 完成，入库 " + tons.stripTrailingZeros().toPlainString() + " 吨",
                        task.getOperator());
            }
        }
        HarvestTask saved = harvestRepository.save(task);
        return toVO(saved);
    }

    /** 调度员确认雨后复工（三项条件由 RainStopService 在锁内逐条校验） */
    @Transactional
    public HarvestVO resume(Long taskId, String operator) {
        return toVO(rainStopService.resume(taskId, operator));
    }

    private String nextTaskNo() {
        return "HS" + LocalDate.now().toString().replace("-", "") + String.format("%03d", harvestRepository.count() + 1);
    }

    public HarvestVO toVO(HarvestTask t) {
        Pond pond = pondRepository.findById(t.getPondId()).orElse(null);
        SaltPlatform platform = t.getPlatformId() == null ? null
                : platformRepository.findById(t.getPlatformId()).orElse(null);
        HarvestVO v = HarvestVO.of(t.getId(), t.getTaskNo(), t.getPondId(),
                pond == null ? null : pond.getPondNo(),
                pond == null ? null : pond.getName(),
                t.getPlatformId(),
                platform == null ? null : platform.getPlatformNo(),
                t.getWorkDate(), t.getTeamName(), t.getStartHour(),
                t.getDurationHours(), t.getTons(), t.getWorkMode(), t.getTaskStatus(), t.getOperator());
        v.setPrevStatus(t.getPrevStatus());
        v.setPrevStatusName(FieldDict.taskStatusName(t.getPrevStatus()));
        v.setStopReason(t.getStopReason());
        v.setStopStartedAt(t.getStopStartedAt());
        v.setResumeAt(t.getResumeAt());
        v.setRainfallSnapshot(t.getRainfallSnapshot());
        if ("RAIN_STOP".equals(t.getTaskStatus()) && pond != null) {
            List<String> blockers = rainStopService.resumeBlockers(pond);
            v.setCanResume(blockers.isEmpty());
            v.setResumeBlockers(blockers);
        } else {
            v.setCanResume(false);
            v.setResumeBlockers(java.util.Collections.emptyList());
        }
        return v;
    }
}
