package com.salt.field.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 气象与卤度记录登记
 */
@Getter
@Setter
public class WeatherCreateRequest {

    @NotNull(message = "池号不能为空")
    private Long pondId;

    private LocalDate recordDate;

    private BigDecimal brineDegree;
    private BigDecimal evaporation;
    private BigDecimal rainfall;
    private BigDecimal temperature;
    private Integer windLevel;
    private String recorder;
}
