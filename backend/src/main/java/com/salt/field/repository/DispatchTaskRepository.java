package com.salt.field.repository;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.salt.field.domain.DispatchTask;

public interface DispatchTaskRepository extends JpaRepository<DispatchTask, Long> {

    @Query("select t from DispatchTask t order by t.planDate asc, t.id asc")
    List<DispatchTask> findAllOrdered();

    /**
     * 派工任务行悲观写锁。所有改状态入口（推进 / 异常修复）都先锁行再判定，
     * 两名调度员同时改同一任务时在数据库层串行化：后到事务看到的是最新状态，
     * 配合 expectedStatus 前置条件，倒退、跳级、覆盖已完成结果都不可能落库。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DispatchTask t where t.id = :id")
    DispatchTask lockById(@Param("id") Long id);
}
