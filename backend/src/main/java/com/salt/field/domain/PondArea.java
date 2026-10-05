package com.salt.field.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 盐田池区：蒸发池区 / 调节池区 / 结晶池区
 */
@Entity
@Table(name = "pond_area")
@Getter
@Setter
public class PondArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** EVAP 蒸发池区 / REG 调节池区 / CRYST 结晶池区 */
    @Column(name = "area_type", nullable = false, length = 16)
    private String areaType;

    /** 池区面积（亩） */
    @Column(name = "area_mu", precision = 10, scale = 2)
    private BigDecimal areaMu;

    /** 纳潮口 */
    @Column(name = "tide_gate", length = 64)
    private String tideGate;

    /** 责任人 */
    @Column(name = "owner_name", length = 32)
    private String ownerName;

    @Column(name = "band_index")
    private Integer bandIndex;

    @Column(name = "remark", length = 128)
    private String remark;
}
