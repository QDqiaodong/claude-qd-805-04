package com.salt.field.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 卤度与气象记录：每日每池的卤度、蒸发量、降雨量、气温、风力。
 * 降雨时间是「雨后 24 小时禁止收盐」这条规则的判据。
 */
@Entity
@Table(name = "weather_log")
@Getter
@Setter
public class WeatherLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pond_id", nullable = false)
    private Long pondId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    /** 测量时刻（判 24 小时窗口用，种子数据落在当天 08:00 / 20:00） */
    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    /** 卤度 °Bé */
    @Column(name = "brine_degree", precision = 5, scale = 2)
    private BigDecimal brineDegree;

    /** 蒸发量 mm */
    @Column(name = "evaporation", precision = 6, scale = 2)
    private BigDecimal evaporation;

    /** 降雨量 mm */
    @Column(name = "rainfall", precision = 6, scale = 2)
    private BigDecimal rainfall;

    /** 气温 ℃ */
    @Column(name = "temperature", precision = 5, scale = 2)
    private BigDecimal temperature;

    /** 风力 级 */
    @Column(name = "wind_level")
    private Integer windLevel;

    @Column(name = "recorder", length = 32)
    private String recorder;
}
