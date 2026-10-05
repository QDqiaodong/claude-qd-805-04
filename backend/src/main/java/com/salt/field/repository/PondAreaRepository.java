package com.salt.field.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.salt.field.domain.PondArea;
import com.salt.field.dto.AreaBrineStat;

public interface PondAreaRepository extends JpaRepository<PondArea, Long> {

    /**
     * 手写 JPQL ①：按池区统计卤度均值（含池子数、总面积亩）
     */
    @Query("select new com.salt.field.dto.AreaBrineStat(a.id, a.name, a.areaType, count(p), avg(p.brineDegree), sum(p.areaMu)) "
            + "from Pond p join p.area a "
            + "group by a.id, a.name, a.areaType "
            + "order by a.bandIndex asc")
    List<AreaBrineStat> statBrineByArea();

    List<PondArea> findAllByOrderByBandIndexAsc();
}
