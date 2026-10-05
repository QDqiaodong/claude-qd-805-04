package com.salt.field.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salt.field.common.BizException;
import com.salt.field.common.FieldDict;
import com.salt.field.domain.HarvestTask;
import com.salt.field.domain.Pond;
import com.salt.field.domain.WeatherLog;
import com.salt.field.dto.PondVO;
import com.salt.field.repository.HarvestTaskRepository;
import com.salt.field.repository.PondRepository;
import com.salt.field.repository.WeatherLogRepository;

/**
 * 「雨后停工 — 复工确认」闭环核心。
 *
 * <p>状态机：PLANNED / WORKING ──有效降雨──▶ RAIN_STOP（保留 prevStatus）
 * ──满 24h + 卤度≥25°Bé + 池板非需修，调度员确认──▶ 恢复 prevStatus。
 * DONE / CANCELED 永不参与停工；RAIN_STOP 不能被人工直接改入/改出。
 *
 * <p>并发口径：所有入口（降雨登记、下达、开始、完成、复工）都在事务内先
 * {@code PESSIMISTIC_WRITE} 锁住池行，同池的雨情与作业变化在数据库层串行化，
 * 杜绝「既运行又停工 / 绕过雨后窗口入账 / 重复停工记录」。
 */
@Service
public class RainStopService {

    public static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PondRepository pondRepository;
    private final WeatherLogRepository weatherRepository;
    private final HarvestTaskRepository harvestRepository;
    private final FieldLogService fieldLogService;

    public RainStopService(PondRepository pondRepository,
                           WeatherLogRepository weatherRepository,
                           HarvestTaskRepository harvestRepository,
                           FieldLogService fieldLogService) {
        this.pondRepository = pondRepository;
        this.weatherRepository = weatherRepository;
        this.harvestRepository = harvestRepository;
        this.fieldLogService = fieldLogService;
    }

    /** 池子当前雨情状态（看板/平面图批量装配用） */
    public static final class RainState {
        public final WeatherLog activeRain;
        public final long stoppedCount;

        RainState(WeatherLog activeRain, long stoppedCount) {
            this.activeRain = activeRain;
            this.stoppedCount = stoppedCount;
        }

        public boolean isActive() {
            return activeRain != null;
        }
    }

    // ---------------- 雨情判据 ----------------

    /** 最近一条有效降雨（可能已超出 24h 窗口；返回顺序保证取首条） */
    public WeatherLog latestEffectiveRain(Long pondId) {
        List<WeatherLog> rains = weatherRepository.findLatestRains(pondId);
        return rains.isEmpty() ? null : rains.get(0);
    }

    /** 雨后窗口内的最新有效降雨；窗口外返回 null（即当前无雨后禁收约束） */
    public WeatherLog activeRain(Long pondId) {
        List<WeatherLog> rains = weatherRepository.findRecentRain(
                pondId, LocalDateTime.now().minusHours(FieldDict.RAIN_BLOCK_HOURS));
        return rains.isEmpty() ? null : rains.get(0);
    }

    /** 最早可复工时刻 = 降雨起算 + 24h */
    public LocalDateTime resumeAt(WeatherLog rain) {
        LocalDateTime start = rain.getRecordedAt();
        return start == null ? null : start.plusHours(FieldDict.RAIN_BLOCK_HOURS);
    }

    // ---------------- 自动停工 ----------------

    /**
     * 登记有效降雨后立即应用到同池作业：
     * <ul>
     *   <li>PLANNED / WORKING → RAIN_STOP，写「自动停工」作业记录；</li>
     *   <li>已在 RAIN_STOP → 以新降雨重新起算窗口，写「窗口顺延」作业记录；</li>
     *   <li>DONE / CANCELED 保持原状；</li>
     *   <li>同一条气象记录重复应用（重试/启动对账）幂等跳过，杜绝重复停工记录。</li>
     * </ul>
     * 调用方必须已持有该池行的悲观写锁。
     *
     * @return 本次实际受影响（新停工 + 重新起算）的作业单数
     */
    @Transactional
    public int applyRainfall(Pond pond, WeatherLog rain, String recorder) {
        if (rain == null || rain.getRainfall() == null || rain.getRainfall().signum() <= 0) {
            return 0;
        }
        List<HarvestTask> live = harvestRepository.findOpenByPond(pond.getId());
        List<HarvestTask> stopped = harvestRepository.findByPondAndStatus(pond.getId(), "RAIN_STOP");
        List<HarvestTask> all = new ArrayList<>(live);
        all.addAll(stopped);
        int affected = 0;
        for (HarvestTask t : all) {
            if (rain.getId() != null && rain.getId().equals(t.getRainLogId())) {
                continue; // 同一降雨已对该作业生效 —— 幂等
            }
            if (FieldDict.isOpenTaskStatus(t.getTaskStatus())) {
                t.setPrevStatus(t.getTaskStatus());
                t.setTaskStatus("RAIN_STOP");
                t.setStopReason(buildReason(rain));
                t.setStopStartedAt(rain.getRecordedAt());
                t.setResumeAt(resumeAt(rain));
                t.setRainLogId(rain.getId());
                t.setRainfallSnapshot(rain.getRainfall());
                harvestRepository.save(t);
                fieldLogService.write("HARVEST", pond,
                        "雨情自动停工：作业单 " + t.getTaskNo() + "（" + t.getTeamName()
                                + "）因 " + rainAt(rain) + " 降雨 "
                                + rain.getRainfall().stripTrailingZeros().toPlainString()
                                + "mm 由「" + FieldDict.taskStatusName(t.getPrevStatus())
                                + "」转为「雨情停工」，最早 " + fmt(resumeAt(rain)) + " 可复工",
                        recorder);
                affected++;
            } else if ("RAIN_STOP".equals(t.getTaskStatus())) {
                // 停工期间又降雨：重新起算窗口，不重复产生停工记录
                t.setStopReason(buildReason(rain));
                t.setStopStartedAt(rain.getRecordedAt());
                t.setResumeAt(resumeAt(rain));
                t.setRainLogId(rain.getId());
                t.setRainfallSnapshot(rain.getRainfall());
                harvestRepository.save(t);
                fieldLogService.write("HARVEST", pond,
                        "雨情窗口顺延：作业单 " + t.getTaskNo() + "（" + t.getTeamName()
                                + "）停工期间 " + rainAt(rain) + " 再降 "
                                + rain.getRainfall().stripTrailingZeros().toPlainString()
                                + "mm，复工窗口重新起算，最早 " + fmt(resumeAt(rain)) + " 可复工",
                        recorder);
                affected++;
            }
        }
        return affected;
    }

    // ---------------- 人工复工 ----------------

    /** 复工拦截项；空列表表示三项条件全部满足 */
    public List<String> resumeBlockers(Pond pond) {
        List<String> blockers = new ArrayList<>();
        WeatherLog rain = activeRain(pond.getId());
        if (rain != null) {
            blockers.add("雨后未满 " + FieldDict.RAIN_BLOCK_HOURS + " 小时：" + pond.getPondNo()
                    + " 于 " + rainAt(rain) + " 降雨 "
                    + rain.getRainfall().stripTrailingZeros().toPlainString()
                    + "mm，最早可复工时刻 " + fmt(resumeAt(rain)));
        }
        BigDecimal brine = pond.getBrineDegree() == null ? BigDecimal.ZERO : pond.getBrineDegree();
        if (brine.compareTo(FieldDict.HARVEST_MIN_BRINE) < 0) {
            blockers.add("最新卤度 " + brine.stripTrailingZeros().toPlainString() + "°Bé，低于门槛 "
                    + FieldDict.HARVEST_MIN_BRINE + "°Bé");
        }
        if ("REPAIR".equals(pond.getBoardStatus())) {
            blockers.add("池板状况为「需修」：" + pond.getPondNo() + " 须先修池验收");
        }
        return blockers;
    }

    /**
     * 调度员确认复工。锁池行 → 复核三项条件（任一不满足逐条列出拦截项）→
     * RAIN_STOP 恢复为停工前状态，并留下人工复工作业记录。
     */
    @Transactional
    public HarvestTask resume(Long taskId, String operator) {
        HarvestTask found = harvestRepository.findById(taskId)
                .orElseThrow(() -> new BizException("作业单不存在：id=" + taskId));
        // 先锁池行（最新雨情/卤度/池板），再锁作业单行；锁上的当前读避免读到停工前快照
        Pond pond = pondRepository.lockById(found.getPondId());
        HarvestTask task = harvestRepository.lockById(taskId);
        if (!"RAIN_STOP".equals(task.getTaskStatus())) {
            throw new BizException("作业单 " + task.getTaskNo() + " 当前为「"
                    + FieldDict.taskStatusName(task.getTaskStatus()) + "」，无需复工确认");
        }

        List<String> blockers = resumeBlockers(pond);
        if (!blockers.isEmpty()) {
            throw new BizException("复工条件不满足：" + String.join("；", blockers));
        }

        String backTo = FieldDict.isOpenTaskStatus(task.getPrevStatus()) ? task.getPrevStatus() : "PLANNED";
        String fromStatus = task.getTaskStatus();
        LocalDateTime startedAt = task.getStopStartedAt();
        BigDecimal rainfall = task.getRainfallSnapshot();

        task.setTaskStatus(backTo);
        task.setPrevStatus(null);
        task.setStopReason(null);
        task.setStopStartedAt(null);
        task.setResumeAt(null);
        task.setRainLogId(null);
        task.setRainfallSnapshot(null);
        HarvestTask saved = harvestRepository.save(task);

        String who = operator == null || operator.isBlank() ? "调度员" : operator;
        fieldLogService.write("HARVEST", pond,
                "雨后复工确认：作业单 " + task.getTaskNo() + "（" + task.getTeamName()
                        + "）由「" + FieldDict.taskStatusName(fromStatus) + "」恢复为「"
                        + FieldDict.taskStatusName(backTo) + "」（停工起算 " + fmt(startedAt)
                        + "，降雨 " + (rainfall == null ? "-" : rainfall.stripTrailingZeros().toPlainString())
                        + "mm；确认人 " + who + "）",
                who);
        return saved;
    }

    // ---------------- 视图装配 ----------------

    /** 批量取一批池子的雨情与停工单数（平面图/池区树/chips 用一次查询装配） */
    public Map<Long, RainState> states(List<Long> pondIds) {
        Map<Long, RainState> result = new HashMap<>();
        if (pondIds == null || pondIds.isEmpty()) {
            return result;
        }
        Map<Long, WeatherLog> active = new HashMap<>();
        for (WeatherLog w : weatherRepository.findRecentRainByPonds(
                pondIds, LocalDateTime.now().minusHours(FieldDict.RAIN_BLOCK_HOURS))) {
            active.putIfAbsent(w.getPondId(), w); // 已按 recordedAt 倒序
        }
        Map<Long, Long> stopped = new HashMap<>();
        for (Object[] row : harvestRepository.countRainStoppedByPonds(pondIds)) {
            stopped.put((Long) row[0], (Long) row[1]);
        }
        for (Long id : pondIds) {
            result.put(id, new RainState(active.get(id), stopped.getOrDefault(id, 0L)));
        }
        return result;
    }

    /** 用最新雨情与停工单数装配单个池子视图（顺便重算 harvestable） */
    public PondVO enrich(PondVO vo, Pond pond) {
        if (vo == null || pond == null) {
            return vo;
        }
        WeatherLog rain = activeRain(pond.getId());
        long stopped = harvestRepository.countRainStoppedByPonds(List.of(pond.getId())).stream()
                .findFirst().map(r -> (Long) r[1]).orElse(0L);
        applyRainToVO(vo, rain, stopped);
        return vo;
    }

    /** 批量装配：与 {@link #states(List)} 配套，避免逐池查库 */
    public void enrichBatch(List<PondVO> vos, Map<Long, RainState> stateMap) {
        if (vos == null) {
            return;
        }
        for (PondVO vo : vos) {
            RainState st = stateMap == null ? null : stateMap.get(vo.getId());
            applyRainToVO(vo, st == null ? null : st.activeRain, st == null ? 0 : st.stoppedCount);
        }
    }

    private void applyRainToVO(PondVO vo, WeatherLog rain, long stoppedCount) {
        vo.setRainActive(rain != null);
        vo.setRainStoppedCount(stoppedCount);
        if (rain != null) {
            vo.setRainAt(rain.getRecordedAt());
            vo.setRainfall(rain.getRainfall());
            vo.setRainResumeAt(resumeAt(rain));
            vo.setHarvestable(false); // 雨后窗口内即使卤度达标也不可收盐
        } else {
            vo.setRainAt(null);
            vo.setRainfall(null);
            vo.setRainResumeAt(null);
        }
    }

    // ---------------- 启动对账 ----------------

    /**
     * 启动时把「池内有窗口内有效降雨、但作业仍处于 PLANNED/WORKING」的单子补做停工，
     * 保证刷新/重启后池子与班次条状态、时间、原因一致；同降雨已生效的幂等跳过。
     * RAIN_STOP 的单子不会被自动复工——复工必须经调度员确认。
     */
    @Transactional
    public int reconcileOnStartup() {
        int affected = 0;
        Map<Long, List<HarvestTask>> byPond = new LinkedHashMap<>();
        for (HarvestTask t : harvestRepository.findAllLive()) {
            byPond.computeIfAbsent(t.getPondId(), k -> new ArrayList<>()).add(t);
        }
        for (Map.Entry<Long, List<HarvestTask>> e : byPond.entrySet()) {
            Pond pond = pondRepository.lockById(e.getKey());
            WeatherLog rain = activeRain(pond.getId());
            if (rain != null) {
                affected += applyRainfall(pond, rain, "系统");
            }
        }
        return affected;
    }

    // ---------------- helpers ----------------

    private String buildReason(WeatherLog rain) {
        return "雨情：" + rainAt(rain) + " 降雨 "
                + rain.getRainfall().stripTrailingZeros().toPlainString()
                + "mm，雨后 " + FieldDict.RAIN_BLOCK_HOURS + " 小时内停工，最早 "
                + fmt(resumeAt(rain)) + " 可复工";
    }

    private String rainAt(WeatherLog rain) {
        return rain.getRecordedAt() == null ? rain.getRecordDate().toString() : fmt(rain.getRecordedAt());
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "-" : t.format(DT);
    }
}
