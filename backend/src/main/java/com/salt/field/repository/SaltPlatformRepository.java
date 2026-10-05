package com.salt.field.repository;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.salt.field.domain.SaltPlatform;
import com.salt.field.dto.PlatformStockStat;

public interface SaltPlatformRepository extends JpaRepository<SaltPlatform, Long> {

    /**
     * 原子「预占冻结」：把可用量（账面 − 待复核预占）作为 UPDATE 的 WHERE 条件一并判定。
     * 行锁 + 条件在同一条语句内完成，并发登记时数据库层串行裁决：
     * 余额不足则影响行数为 0（调用方据此拒绝），从根上杜绝读-判-写窗口导致的超卖。
     *
     * @return 受影响行数：1 成功；0 表示可用库存不足（本次登记必须整体回滚）
     */
    @Modifying
    @Query(value = "UPDATE salt_platform p SET p.stock_tons = p.stock_tons "
            + "WHERE p.id = :id AND p.stock_tons - COALESCE("
            + "(SELECT SUM(o.tons) FROM outbound_record o WHERE o.platform_id = :id AND o.status = 'PENDING'), 0) "
            + ">= :tons", nativeQuery = true)
    int tryReserve(@Param("id") Long id, @Param("tons") java.math.BigDecimal tons);

    /**
     * 原子「预占转实扣」：仅当账面库存足够时，才把账面扣减（预占量由单据状态翻转为
     * CONFIRMED 而自然移出）。影响行数 0 即库存不足。
     */
    @Modifying
    @Query(value = "UPDATE salt_platform SET stock_tons = stock_tons - :tons "
            + "WHERE id = :id AND stock_tons >= :tons", nativeQuery = true)
    int tryDeduct(@Param("id") Long id, @Param("tons") java.math.BigDecimal tons);

    /** 冲正恢复：账面无条件加回原吨数（调用方已锁原单、状态保证只恢复一次） */
    @Modifying
    @Query(value = "UPDATE salt_platform SET stock_tons = stock_tons + :tons WHERE id = :id", nativeQuery = true)
    void addStock(@Param("id") Long id, @Param("tons") java.math.BigDecimal tons);

    /**
     * 坨台行悲观写锁：登记预占 / 确认实扣 / 作废释放 / 冲正恢复 都先锁坨台行，
     * 同坨的库存与预占变化在数据库层串行化。跨坨冲正时按平台 id 升序依次加锁防死锁。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SaltPlatform p where p.id = :id")
    SaltPlatform lockById(@Param("id") Long id);

    /**
     * 手写 JPQL ⑥：坨台库存、预占与实发汇总。
     * 口径：PENDING 计入 reservedTons（登记预占，未扣账面）；CONFIRMED 计入 outTons（已实扣）；
     * REJECTED / REVERSED 既不预占也不计实发。
     */
    @Query("select new com.salt.field.dto.PlatformStockStat(p.id, p.platformNo, p.name, p.saltType, p.stockTons, "
            + "p.capacityTons, "
            + "coalesce(sum(case when o.status = 'CONFIRMED' then o.tons else 0 end), 0), "
            + "coalesce(sum(case when o.status = 'PENDING' then 1 else 0 end), 0), "
            + "coalesce(sum(case when o.status = 'PENDING' then o.tons else 0 end), 0)) "
            + "from SaltPlatform p left join OutboundRecord o on o.platformId = p.id "
            + "group by p.id, p.platformNo, p.name, p.saltType, p.stockTons, p.capacityTons "
            + "order by p.platformNo asc")
    List<PlatformStockStat> statStockAndOutbound();

    @Query("select coalesce(sum(p.stockTons), 0) from SaltPlatform p")
    java.math.BigDecimal sumStock();
}
