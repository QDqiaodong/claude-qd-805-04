package com.salt.field.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.salt.field.domain.DispatchStatusLog;

public interface DispatchStatusLogRepository extends JpaRepository<DispatchStatusLog, Long> {

    List<DispatchStatusLog> findByTaskIdOrderByOperatedAtAscIdAsc(Long taskId);
}
