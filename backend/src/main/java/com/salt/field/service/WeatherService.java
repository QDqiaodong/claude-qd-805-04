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
import com.salt.field.domain.Pond;
import com.salt.field.domain.WeatherLog;
import com.salt.field.dto.DailyWeatherStat;
import com.salt.field.dto.WeatherCreateRequest;
import com.salt.field.repository.PondRepository;
import com.salt.field.repository.WeatherLogRepository;

@Service
public class WeatherService {

    private final WeatherLogRepository weatherRepository;
    private final PondRepository pondRepository;
    private final RainStopService rainStopService;

    public WeatherService(WeatherLogRepository weatherRepository,
                          PondRepository pondRepository,
                          RainStopService rainStopService) {
        this.weatherRepository = weatherRepository;
        this.pondRepository = pondRepository;
        this.rainStopService = rainStopService;
    }

    /** 近 N 天的日汇总（蒸发量 / 降雨量 / 平均卤度 / 是否可收盐） */
    public List<Map<String, Object>> dailySeries(int days) {
        int n = days <= 0 ? 14 : days;
        LocalDate from = LocalDate.now().minusDays(n - 1L);
        List<DailyWeatherStat> stats = weatherRepository.statDaily(from);
        Map<LocalDate, DailyWeatherStat> byDate = new LinkedHashMap<>();
        stats.forEach(s -> byDate.put(s.recordDate(), s));

        List<Map<String, Object>> series = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            DailyWeatherStat s = byDate.get(date);
            BigDecimal evap = s == null || s.evaporationTotal() == null ? BigDecimal.ZERO : s.evaporationTotal();
            BigDecimal rain = s == null || s.rainfallTotal() == null ? BigDecimal.ZERO : s.rainfallTotal();
            Double temp = s == null ? null : s.avgTemperature();
            Double brine = s == null ? null : s.avgBrine();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordDate", date);
            row.put("evaporation", evap);
            row.put("rainfall", rain);
            row.put("temperature", temp);
            row.put("avgBrine", brine);
            row.put("recordCount", s == null ? 0L : s.recordCount());
            row.put("rainy", rain.compareTo(BigDecimal.ZERO) > 0);
            row.put("harvestable", rain.compareTo(BigDecimal.ZERO) == 0
                    && brine != null && brine >= FieldDict.HARVEST_MIN_BRINE.doubleValue());
            series.add(row);
        }
        return series;
    }

    public List<WeatherLog> byPond(Long pondId) {
        return weatherRepository.findByPondIdOrderByRecordDateDesc(pondId);
    }

    public List<WeatherLog> byDate(LocalDate date) {
        return weatherRepository.findByDate(date == null ? LocalDate.now() : date);
    }

    /** 雨后 24 小时内的降雨记录（收盐规则判据） */
    public List<WeatherLog> recentRain(Long pondId) {
        return weatherRepository.findRecentRain(pondId, LocalDateTime.now().minusHours(FieldDict.RAIN_BLOCK_HOURS));
    }

    public WeatherLog latestRain(Long pondId) {
        List<WeatherLog> rains = recentRain(pondId);
        return rains.isEmpty() ? null : rains.get(0);
    }

    /**
     * 登记气象/卤度记录。
     *
     * <p>闭环关键路径：当本次为有效降雨（rainfall &gt; 0）时，在同一事务、同一池行悲观锁内，
     * 立即把同池尚未完成（PLANNED/WORKING）的收盐作业转为「雨情停工」，
     * 已停工的则以新降雨重新起算 24h 窗口；DONE/CANCELED 不动。
     */
    @Transactional
    public Map<String, Object> create(WeatherCreateRequest request) {
        if (!pondRepository.existsById(request.getPondId())) {
            throw new BizException("池子不存在：id=" + request.getPondId());
        }
        // 先锁池行：与作业开始/完成/复工确认串行化，避免边录雨边推进作业
        Pond pond = pondRepository.lockById(request.getPondId());
        WeatherLog log = new WeatherLog();
        log.setPondId(pond.getId());
        log.setRecordDate(request.getRecordDate() == null ? LocalDate.now() : request.getRecordDate());
        log.setRecordedAt(LocalDateTime.now());
        log.setBrineDegree(nz(request.getBrineDegree()));
        log.setEvaporation(nz(request.getEvaporation()));
        log.setRainfall(nz(request.getRainfall()));
        log.setTemperature(request.getTemperature());
        log.setWindLevel(request.getWindLevel() == null ? 0 : request.getWindLevel());
        log.setRecorder(request.getRecorder() == null ? "气象员" : request.getRecorder());
        if (log.getBrineDegree().signum() > 0) {
            pond.setBrineDegree(log.getBrineDegree());
            pondRepository.save(pond);
        }
        WeatherLog saved = weatherRepository.save(log);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weatherLog", saved);
        result.put("rainfall", saved.getRainfall());
        result.put("rainActive", saved.getRainfall().signum() > 0);
        if (saved.getRainfall().signum() > 0) {
            // 有效降雨：立即停工 / 重新起算窗口（持锁调用，同事务提交）
            int affected = rainStopService.applyRainfall(pond, saved, saved.getRecorder());
            result.put("stopStartedAt", saved.getRecordedAt());
            result.put("resumeAt", saved.getRecordedAt().plusHours(FieldDict.RAIN_BLOCK_HOURS));
            result.put("stoppedTaskCount", affected);
        } else {
            result.put("stopStartedAt", null);
            result.put("resumeAt", null);
            result.put("stoppedTaskCount", 0);
        }
        return result;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
