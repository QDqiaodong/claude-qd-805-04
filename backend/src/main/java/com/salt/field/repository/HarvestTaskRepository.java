package com.salt.field.repository;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.salt.field.domain.HarvestTask;
import com.salt.field.dto.TeamHarvestStat;

public interface HarvestTaskRepository extends JpaRepository<HarvestTask, Long> {

    /**
     * 作业单行悲观写锁。与池行锁配合：所有改状态的入口都按「先锁池、再锁单」的统一顺序加锁，
     * 既与降雨登记（持池锁后 UPDATE 作业单）串行，也避免 InnoDB 可重复读快照读到停工前旧状态。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from HarvestTask t where t.id = :id")
    HarvestTask lockById(@Param("id") Long id);

    @Query("select t from HarvestTask t where t.workDate = :date order by t.teamName asc, t.startHour asc")
    List<HarvestTask> findByDate(@Param("date") LocalDate date);

    /**
     * 手写 JPQL ⑤：按班组汇总收盐吨数与作业时长（班次条右侧统计）
     */
    @Query("select new com.salt.field.dto.TeamHarvestStat(t.teamName, sum(t.tons), sum(t.durationHours), count(t)) "
            + "from HarvestTask t where t.workDate = :date and t.taskStatus <> 'CANCELED' "
            + "group by t.teamName "
            + "order by sum(t.tons) desc")
    List<TeamHarvestStat> statByTeam(@Param("date") LocalDate date);

    @Query("select coalesce(sum(t.tons), 0) from HarvestTask t where t.workDate = :date and t.taskStatus = 'DONE'")
    java.math.BigDecimal sumDoneTons(@Param("date") LocalDate date);

    @Query("select t from HarvestTask t where t.pondId = :pondId order by t.workDate desc")
    List<HarvestTask> findByPond(@Param("pondId") Long pondId);

    /** 同池尚未完成的作业：待作业 / 作业中（有效降雨登记后立即停工的对象） */
    @Query("select t from HarvestTask t "
            + "where t.pondId = :pondId and t.taskStatus in ('PLANNED','WORKING') "
            + "order by t.workDate asc, t.startHour asc, t.id asc")
    List<HarvestTask> findOpenByPond(@Param("pondId") Long pondId);

    /** 同池指定状态的作业（停工期间再降雨、重新起算窗口用） */
    @Query("select t from HarvestTask t where t.pondId = :pondId and t.taskStatus = :status order by t.id asc")
    List<HarvestTask> findByPondAndStatus(@Param("pondId") Long pondId, @Param("status") String status);

    /** 一批池子的雨情停工单数（平面图/池区树角标用） */
    @Query("select t.pondId, count(t) from HarvestTask t "
            + "where t.pondId in :pondIds and t.taskStatus = 'RAIN_STOP' "
            + "group by t.pondId order by t.pondId asc")
    List<Object[]> countRainStoppedByPonds(@Param("pondIds") List<Long> pondIds);

    /** 全库进行中/待作业与已停工的作业（启动时按最新雨情重对账用） */
    @Query("select t from HarvestTask t where t.taskStatus in ('PLANNED','WORKING','RAIN_STOP') order by t.id asc")
    List<HarvestTask> findAllLive();
}
