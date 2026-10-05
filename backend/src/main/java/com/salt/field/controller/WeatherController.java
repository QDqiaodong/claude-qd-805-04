package com.salt.field.controller;

import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.salt.field.common.ApiResponse;
import com.salt.field.domain.WeatherLog;
import com.salt.field.dto.WeatherCreateRequest;
import com.salt.field.service.WeatherService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /** 近 N 天蒸发量 / 降雨量日汇总 */
    @GetMapping("/daily")
    public ApiResponse<List<Map<String, Object>>> daily(@RequestParam(defaultValue = "14") int days) {
        return ApiResponse.success(weatherService.dailySeries(days));
    }

    @GetMapping("/pond/{pondId}")
    public ApiResponse<List<WeatherLog>> byPond(@PathVariable Long pondId) {
        return ApiResponse.success(weatherService.byPond(pondId));
    }

    /** 该池雨后 24 小时内的降雨记录（收盐规则的判据） */
    @GetMapping("/pond/{pondId}/rain")
    public ApiResponse<List<WeatherLog>> recentRain(@PathVariable Long pondId) {
        return ApiResponse.success(weatherService.recentRain(pondId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody WeatherCreateRequest request) {
        Map<String, Object> result = weatherService.create(request);
        boolean rain = Boolean.TRUE.equals(result.get("rainActive"));
        int stopped = ((Number) result.getOrDefault("stoppedTaskCount", 0)).intValue();
        String msg = rain
                ? "有效降雨已登记，同池 " + stopped + " 单未完成作业已进入雨情停工（24 小时内禁止收盐）"
                : "气象与卤度记录已登记";
        return ApiResponse.success(msg, result);
    }

    @GetMapping("/date")
    public ApiResponse<List<WeatherLog>> byDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                java.time.LocalDate date) {
        return ApiResponse.success(weatherService.byDate(date));
    }
}
