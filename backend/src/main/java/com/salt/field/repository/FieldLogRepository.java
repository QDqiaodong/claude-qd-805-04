package com.salt.field.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.salt.field.domain.FieldLog;

public interface FieldLogRepository extends JpaRepository<FieldLog, Long> {

    /** {@code @Any} 多态关联查询：按目标类型与主键查某对象的全部作业记录 */
    List<FieldLog> findTop20ByOrderByLogTimeDesc();
}
