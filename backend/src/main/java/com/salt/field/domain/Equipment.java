package com.salt.field.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 盐场设备：收盐机、提卤泵、推土机等。{@code @Any} 的第三类目标对象（target_type = EQUIPMENT）。
 */
@Entity
@Table(name = "equipment")
@Getter
@Setter
public class Equipment extends SaltTarget {

    @Column(name = "equip_no", nullable = false, unique = true, length = 32)
    private String equipNo;

    /** 设备类型：收盐机 / 提卤泵 / 推土机 / 输送带 */
    @Column(name = "equip_type", length = 32)
    private String equipType;

    /** 状态：RUNNING 在用 / IDLE 闲置 / FAULT 故障 */
    @Column(name = "equip_status", length = 16)
    private String equipStatus;

    @Column(name = "location", length = 64)
    private String location;

    @Column(name = "last_maintain")
    private LocalDate lastMaintain;

    @Override
    public String targetType() {
        return "EQUIPMENT";
    }
}
