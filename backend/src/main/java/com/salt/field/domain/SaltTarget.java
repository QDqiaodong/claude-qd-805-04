package com.salt.field.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * 盐场多态对象基类：池子 / 坨台 / 设备 都继承它，
 * 因此 {@code field_log}、{@code dispatch_task} 可以用 Hibernate 6 的 {@code @Any}
 * 把「一条记录指向任意一种对象」映射成真正的多态关联，而不是自己写 if-else 去查。
 */
@MappedSuperclass
public abstract class SaltTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对象名称（池名 / 坨台名 / 设备名），多态关联回显时直接用它 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 多态类型标识，与 field_log.target_type / dispatch_task.target_type 的取值一致 */
    public abstract String targetType();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
