package com.salt.field.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 池子台账：所属池区、面积亩、池板状况、当前卤度、卤水深度、作业状态。
 * 同时它是 {@code @Any} 多态关联的一类目标对象（target_type = POND）。
 */
@Entity
@Table(name = "pond")
@Getter
@Setter
public class Pond extends SaltTarget {

    @Column(name = "pond_no", nullable = false, unique = true, length = 32)
    private String pondNo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "area_id")
    private PondArea area;

    /** 面积（亩） */
    @Column(name = "area_mu", precision = 10, scale = 2)
    private BigDecimal areaMu;

    /** 池板状况：GOOD 完好 / LEAK 渗漏 / REPAIR 需修 */
    @Column(name = "board_status", length = 16)
    private String boardStatus;

    /** 当前卤度 °Bé */
    @Column(name = "brine_degree", precision = 5, scale = 2)
    private BigDecimal brineDegree;

    /** 卤水深度 cm */
    @Column(name = "brine_depth", precision = 5, scale = 2)
    private BigDecimal brineDepth;

    /** 状态：SUN 晒卤 / CRYST 结晶 / WAIT 待收 / REPAIR 修池 */
    @Column(name = "pond_status", length = 16)
    private String pondStatus;

    /** 平面图行号/列号，前端据此绘制 SVG 平面 */
    @Column(name = "grid_row")
    private Integer gridRow;

    @Column(name = "grid_col")
    private Integer gridCol;

    @Override
    public String targetType() {
        return "POND";
    }
}
