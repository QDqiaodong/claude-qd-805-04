package com.salt.field.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.Any;
import org.hibernate.annotations.AnyDiscriminator;
import org.hibernate.annotations.AnyDiscriminatorValue;
import org.hibernate.annotations.AnyDiscriminatorValues;
import org.hibernate.annotations.AnyKeyJavaClass;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 作业/巡检记录。核心能力：一条记录可以指向「池子 / 坨台 / 设备」三种不同实体中的任意一种，
 * 用 Hibernate 6 的 {@code @Any} 多态关联映射（target_type 存类型标识，target_id 存主键）。
 */
@Entity
@Table(name = "field_log")
@Getter
@Setter
public class FieldLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 记录类型：BRINE 测卤 / HARVEST 收盐 / OUTBOUND 出仓 / REPAIR 修池 / INSPECT 巡检 */
    @Column(name = "log_type", length = 16)
    private String logType;

    @Column(name = "content", length = 255)
    private String content;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "log_time")
    private LocalDateTime logTime;

    @Any
    @AnyDiscriminator(DiscriminatorType.STRING)
    @AnyDiscriminatorValues({
            @AnyDiscriminatorValue(discriminator = "POND", entity = Pond.class),
            @AnyDiscriminatorValue(discriminator = "PLATFORM", entity = SaltPlatform.class),
            @AnyDiscriminatorValue(discriminator = "EQUIPMENT", entity = Equipment.class)
    })
    @AnyKeyJavaClass(Long.class)
    @Column(name = "target_type", length = 16)
    @JoinColumn(name = "target_id")
    private SaltTarget target;
}
