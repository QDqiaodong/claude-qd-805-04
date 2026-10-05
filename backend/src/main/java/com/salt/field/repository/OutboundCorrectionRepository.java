package com.salt.field.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.salt.field.domain.OutboundCorrection;

public interface OutboundCorrectionRepository extends JpaRepository<OutboundCorrection, Long> {

    /** 一张单的更正痕迹（可能多次直改车牌；冲正只允许一次） */
    List<OutboundCorrection> findByRecordIdOrderByOperatedAtAscIdAsc(Long recordId);

    /** 重记新单反查冲正来源流水（新单详情展示「冲正自 #原单」） */
    List<OutboundCorrection> findByNewRecordIdOrderByOperatedAtAscIdAsc(Long newRecordId);
}
