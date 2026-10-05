package com.salt.field.repository;

import java.math.BigDecimal;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.salt.field.domain.OutboundRecord;

public interface OutboundRecordRepository extends JpaRepository<OutboundRecord, Long> {

    /**
     * 出仓单行悲观写锁。所有改单入口（确认 / 作废 / 车牌直改 / 冲正）都与坨台行锁
     * 按「先锁坨台、再锁单」的统一顺序加锁，同坨库存变化与单据状态变化在数据库层串行化，
     * 杜绝并发确认、重复冲正、库存恢复两次。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboundRecord o where o.id = :id")
    OutboundRecord lockById(@Param("id") Long id);

    List<OutboundRecord> findByPlatformIdOrderByOutDateDescIdDesc(Long platformId);

    /** 全部出仓单（复核队列用），新登记在前 */
    List<OutboundRecord> findAllByOrderByCreatedAtDescIdDesc();

    List<OutboundRecord> findByStatusOrderByCreatedAtDescIdDesc(String status);

    /** 某坨台待复核（预占中）吨数：登记即冻结、确认/作废/冲正后自动移出 */
    @Query("select coalesce(sum(o.tons), 0) from OutboundRecord o "
            + "where o.platformId = :platformId and o.status = 'PENDING'")
    BigDecimal sumPendingTons(@Param("platformId") Long platformId);

    /** 各坨台待复核预占吨数（卡片 / 汇总表一次装配） */
    @Query("select o.platformId, coalesce(sum(o.tons), 0) from OutboundRecord o "
            + "where o.status = 'PENDING' group by o.platformId")
    List<Object[]> sumPendingTonsByPlatform();

    /** 某坨台累计实发：只有已确认单参与；已冲正单不再计入累计发运 */
    @Query("select coalesce(sum(o.tons), 0) from OutboundRecord o "
            + "where o.platformId = :platformId and o.status = 'CONFIRMED'")
    BigDecimal sumConfirmedTons(@Param("platformId") Long platformId);
}
