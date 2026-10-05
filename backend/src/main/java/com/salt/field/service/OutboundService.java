package com.salt.field.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.salt.field.common.BizException;
import com.salt.field.domain.OutboundCorrection;
import com.salt.field.domain.OutboundRecord;
import com.salt.field.domain.SaltPlatform;
import com.salt.field.dto.CorrectionVO;
import com.salt.field.dto.OutboundRequest;
import com.salt.field.dto.OutboundReversalRequest;
import com.salt.field.dto.OutboundVO;
import com.salt.field.dto.PlateCorrectionRequest;
import com.salt.field.repository.OutboundCorrectionRepository;
import com.salt.field.repository.OutboundRecordRepository;
import com.salt.field.repository.SaltPlatformRepository;

import jakarta.persistence.EntityManager;

/**
 * 出仓「登记预占 — 复核确认 — 冲正重记」流程核心。
 *
 * <p><b>库存口径：优先预占后实扣。</b>
 * <ul>
 *   <li>登记（PENDING）：只冻结（预占）吨数，账面库存不动，可用库存立即减少；</li>
 *   <li>复核确认（CONFIRMED）：预占转为实扣，账面库存才真正减少；</li>
 *   <li>复核作废（REJECTED）：仅释放预占，账面从未变化，无账实差异；</li>
 *   <li>冲正重记：原单 REVERSED、库存原子恢复，同事务内按新单再扣减。</li>
 * </ul>
 *
 * <p><b>并发口径</b>（与雨后停工闭环同一套做法）：所有入口都在事务内先对坨台行加
 * {@code PESSIMISTIC_WRITE}，再对出仓单行加锁；跨坨冲正按坨台 id 升序依次加锁防死锁。
 * 同坨的库存、预占与单据状态在数据库层串行化，杜绝并发确认；冲正三重防重
 * （状态判断 + 行锁 + {@code uk_outbound_reversal_of} 唯一约束），恢复只会发生一次。
 *
 * <p><b>留痕与删除</b>：车牌直改写 PLATE 流水、冲正重记写 REVERSAL 流水，原值 / 新值 /
 * 操作人 / 时间全部保留；任何已确认记录不得删除（系统也不提供删除入口），REVERSED /
 * REJECTED 单据永久留在流水里，刷新后状态由持久化字段装配，前后一致。
 */
@Service
public class OutboundService {

    private final SaltPlatformRepository platformRepository;
    private final OutboundRecordRepository outboundRepository;
    private final OutboundCorrectionRepository correctionRepository;
    private final FieldLogService fieldLogService;
    private final EntityManager entityManager;

    public OutboundService(SaltPlatformRepository platformRepository,
                           OutboundRecordRepository outboundRepository,
                           OutboundCorrectionRepository correctionRepository,
                           FieldLogService fieldLogService,
                           EntityManager entityManager) {
        this.platformRepository = platformRepository;
        this.outboundRepository = outboundRepository;
        this.correctionRepository = correctionRepository;
        this.fieldLogService = fieldLogService;
        this.entityManager = entityManager;
    }

    // ---------------- 登记：预占 ----------------

    /**
     * 登记出仓：用一条「条件 UPDATE」原子完成预占校验（账面 − 待复核预占 ≥ 本次吨数），
     * 数据库层裁决并发登记、杜绝超卖；账面库存不动，只新增 PENDING 单冻结吨数。
     */
    @Transactional
    public OutboundVO register(Long platformId, OutboundRequest request) {
        BigDecimal tons = positiveTons(request.getTons());
        SaltPlatform platform = mustPlatform(platformId);
        platform = platformRepository.lockById(platformId); // 同坨登记在此串行

        BigDecimal reserved = outboundRepository.sumPendingTons(platformId);
        BigDecimal available = nz(platform.getStockTons()).subtract(nz(reserved));
        // 原子条件裁决：余额不足则影响行数 0，本次登记整体回滚、不留半截预占
        if (platformRepository.tryReserve(platformId, tons) == 0) {
            throw new BizException("可用库存不足：坨台 " + platform.getPlatformNo() + " 账面 "
                    + txt(platform.getStockTons()) + " 吨、待复核预占 " + txt(reserved)
                    + " 吨、可用 " + txt(available) + " 吨，本次登记 " + txt(tons) + " 吨");
        }

        OutboundRecord record = new OutboundRecord();
        record.setPlatformId(platform.getId());
        record.setCustomer(request.getCustomer());
        record.setTons(tons);
        record.setPlateNo(request.getPlateNo());
        record.setOutDate(LocalDate.now());
        record.setOperator(request.getOperator() == null || request.getOperator().isBlank()
                ? "司磅员" : request.getOperator());
        record.setStatus("PENDING");
        record.setCreatedAt(LocalDateTime.now());
        outboundRepository.saveAndFlush(record);

        fieldLogService.write("OUTBOUND", platform,
                "登记出仓（待复核，预占 " + txt(tons) + " 吨）→ " + request.getCustomer()
                        + "（" + plate(request.getPlateNo()) + "），确认前不扣账面库存",
                record.getOperator());
        return toVO(record, platform);
    }

    // ---------------- 复核：确认实扣 / 作废释放 ----------------

    /**
     * 复核确认：PENDING → CONFIRMED，预占转实扣（账面库存 −吨数）。并发确认时第二个请求
     * 因行锁后状态已不是 PENDING 而被拒绝。
     */
    @Transactional
    public OutboundVO confirm(Long id, String operator) {
        OutboundRecord record = outboundRepository.findById(id)
                .orElseThrow(() -> new BizException("出仓单不存在：id=" + id));
        SaltPlatform platform = platformRepository.lockById(record.getPlatformId()); // 先锁坨台
        record = outboundRepository.lockById(id);                                  // 再锁单
        if (!"PENDING".equals(record.getStatus())) {
            throw new BizException("出仓单 #" + id + " 当前为「"
                    + com.salt.field.common.FieldDict.outboundStatusName(record.getStatus())
                    + "」，只有待复核单可以确认");
        }
        BigDecimal tons = record.getTons();

        // 原子条件实扣：账面 ≥ 吨数才扣，避免任何读-判-写窗口；正常因登记已预占必成功
        if (platformRepository.tryDeduct(record.getPlatformId(), tons) == 0) {
            throw new BizException("账面库存不足，无法确认实扣：坨台 " + platform.getPlatformNo()
                    + " 现存 " + txt(platform.getStockTons()) + " 吨，本单 " + txt(tons) + " 吨");
        }
        entityManager.refresh(platform);
        BigDecimal stockAfter = platform.getStockTons();

        String who = who(operator, "复核员");
        record.setStatus("CONFIRMED");
        record.setConfirmOperator(who);
        record.setConfirmAt(LocalDateTime.now());
        outboundRepository.save(record);

        fieldLogService.write("OUTBOUND", platform,
                "出仓复核确认：#" + id + " 预占转实扣 " + txt(tons) + " 吨 → " + record.getCustomer()
                        + "（" + plate(record.getPlateNo()) + "），账面库存减至 "
                        + txt(stockAfter) + " 吨",
                who);
        return toVO(record, platform);
    }

    /**
     * 复核作废：PENDING → REJECTED，释放预占。账面库存从未扣过，无需恢复。
     */
    @Transactional
    public OutboundVO reject(Long id, String operator, String reason) {
        OutboundRecord found = outboundRepository.findById(id)
                .orElseThrow(() -> new BizException("出仓单不存在：id=" + id));
        SaltPlatform platform = platformRepository.lockById(found.getPlatformId());
        OutboundRecord record = outboundRepository.lockById(id);
        if (!"PENDING".equals(record.getStatus())) {
            throw new BizException("出仓单 #" + id + " 当前为「"
                    + com.salt.field.common.FieldDict.outboundStatusName(record.getStatus())
                    + "」，只有待复核单可以作废");
        }
        String who = who(operator, "复核员");
        record.setStatus("REJECTED");
        record.setRejectOperator(who);
        record.setRejectAt(LocalDateTime.now());
        record.setRejectReason(reason);
        outboundRepository.save(record);

        fieldLogService.write("OUTBOUND", platform,
                "出仓复核作废：#" + id + " 释放预占 " + txt(record.getTons()) + " 吨（"
                        + (reason == null || reason.isBlank() ? "无" : reason) + "）",
                who);
        return toVO(record, platform);
    }

    // ---------------- 纠错路径一：仅车牌直改 ----------------

    /**
     * 仅车牌错误直接更正：坨台、吨数（及客户）保持不变，不动库存与预占。
     * 待复核 / 已确认单都允许；已作废 / 已冲正单不允许。每次直改都写一条 PLATE 流水。
     */
    @Transactional
    public OutboundVO correctPlate(Long id, PlateCorrectionRequest request) {
        OutboundRecord found = outboundRepository.findById(id)
                .orElseThrow(() -> new BizException("出仓单不存在：id=" + id));
        SaltPlatform platform = platformRepository.lockById(found.getPlatformId());
        OutboundRecord record = outboundRepository.lockById(id);
        String status = record.getStatus();
        if (!"PENDING".equals(status) && !"CONFIRMED".equals(status)) {
            throw new BizException("出仓单 #" + id + " 当前为「"
                    + com.salt.field.common.FieldDict.outboundStatusName(status)
                    + "」，不能更正车牌");
        }
        String newPlate = request.getNewPlateNo() == null ? "" : request.getNewPlateNo().trim();
        String oldPlate = record.getPlateNo() == null ? "" : record.getPlateNo();
        if (newPlate.equals(oldPlate)) {
            throw new BizException("新车牌与原车牌相同，无需更正");
        }

        OutboundCorrection c = new OutboundCorrection();
        c.setChangeType("PLATE");
        c.setRecordId(id);
        c.setOldPlatformId(record.getPlatformId());
        c.setNewPlatformId(record.getPlatformId());
        c.setOldCustomer(record.getCustomer());
        c.setNewCustomer(record.getCustomer());
        c.setOldTons(record.getTons());
        c.setNewTons(record.getTons());
        c.setOldPlateNo(record.getPlateNo());
        c.setNewPlateNo(request.getNewPlateNo().trim());
        c.setOperator(who(request.getOperator(), "司磅员"));
        c.setOperatedAt(LocalDateTime.now());
        c.setRemark(request.getRemark());
        correctionRepository.save(c);

        record.setPlateNo(request.getNewPlateNo().trim());
        outboundRepository.save(record);

        fieldLogService.write("OUTBOUND", platform,
                "出仓车牌更正：#" + id + "（" + com.salt.field.common.FieldDict.outboundStatusName(status)
                        + "）车牌 " + plate(oldPlate) + " → " + plate(newPlate)
                        + "，坨台 / 吨数不变，库存不动",
                c.getOperator());
        return toVO(record, platform);
    }

    // ---------------- 纠错路径二：冲正 → 重记（原子） ----------------

    /**
     * 已确认单冲正后按新单重记，整过程一个事务、原子完成：
     * <ol>
     *   <li>按 id 升序锁住原坨台与目标坨台，再锁原单（跨坨防死锁 / 串行化）；</li>
     *   <li>原单 CONFIRMED → REVERSED，原坨台库存 <b>先恢复</b> 原吨数；</li>
     *   <li>校验目标坨台可用库存后 <b>再按新单扣减</b>，新单直接 CONFIRMED；</li>
     *   <li>写 REVERSAL 更正流水（原值 / 新值 / 操作人 / 时间），双向挂冲正链路。</li>
     * </ol>
     * 禁止重复冲正：状态判断 + 行锁 + 新单 reversal_of_id 唯一约束三重兜底；
     * 任一步失败整事务回滚，不会出现「恢复了没扣 / 扣了没恢复 / 恢复两次」。
     */
    @Transactional
    public OutboundVO reverseAndReRecord(Long id, OutboundReversalRequest request) {
        BigDecimal newTons = positiveTons(request.getTons());
        OutboundRecord original = outboundRepository.findById(id)
                .orElseThrow(() -> new BizException("出仓单不存在：id=" + id));
        if (!"CONFIRMED".equals(original.getStatus())) {
            throw new BizException("出仓单 #" + id + " 当前为「"
                    + com.salt.field.common.FieldDict.outboundStatusName(original.getStatus())
                    + "」，只有已确认单需要冲正重记；待复核单请直接作废后重新登记");
        }
        if (original.getReversedById() != null) {
            throw new BizException("出仓单 #" + id + " 已被冲正（重记单 #" + original.getReversedById()
                    + "），禁止重复冲正");
        }
        Long fromId = original.getPlatformId();
        Long toId = request.getPlatformId();
        mustPlatform(fromId);
        SaltPlatform target = mustPlatform(toId);

        // 统一按坨台 id 升序加锁，跨坨操作也不会与反向流程死锁
        List<Long> platformIds = fromId.equals(toId) ? List.of(fromId)
                : (fromId < toId ? List.of(fromId, toId) : List.of(toId, fromId));
        Map<Long, SaltPlatform> locked = new LinkedHashMap<>();
        for (Long pid : platformIds) {
            locked.put(pid, platformRepository.lockById(pid));
        }
        SaltPlatform from = locked.get(fromId);
        target = locked.get(toId);
        original = outboundRepository.lockById(id);

        // 锁上再判一次：并发冲正的第二个事务在此被拒
        if (!"CONFIRMED".equals(original.getStatus()) || original.getReversedById() != null) {
            throw new BizException("出仓单 #" + id + " 已不是可冲正状态，可能已被并发冲正，请刷新后重试");
        }
        BigDecimal oldTons = original.getTons();

        // ① 库存恢复（原坨台，原子加回）——冲正三重重防保证只会执行一次
        platformRepository.addStock(fromId, oldTons);
        entityManager.refresh(from);

        // ② 按新单扣减（目标坨台；原子条件：账面 ≥ 新吨数）
        if (platformRepository.tryDeduct(toId, newTons) == 0) {
            throw new BizException("冲正已恢复库存，但重记新单账面库存不足：坨台 " + target.getPlatformNo()
                    + " 现存 " + txt(target.getStockTons()) + " 吨，新单 " + txt(newTons)
                    + " 吨。本次冲正整体回滚，原单仍为已确认");
        }
        entityManager.refresh(target);

        // ③ 新单直接 CONFIRMED，并以 reversal_of_id 唯一约束作为重复冲正的最后防线
        OutboundRecord rerecord = new OutboundRecord();
        rerecord.setPlatformId(toId);
        rerecord.setCustomer(request.getCustomer());
        rerecord.setTons(newTons);
        rerecord.setPlateNo(request.getPlateNo());
        rerecord.setOutDate(original.getOutDate()); // 业务发生日期沿用原单
        rerecord.setOperator(original.getOperator());
        rerecord.setStatus("CONFIRMED");
        rerecord.setConfirmOperator(who(request.getOperator(), "司磅员"));
        rerecord.setConfirmAt(LocalDateTime.now());
        rerecord.setReversalOfId(id);
        rerecord.setCreatedAt(LocalDateTime.now());
        try {
            outboundRepository.saveAndFlush(rerecord);
        } catch (DataIntegrityViolationException e) {
            // uk_outbound_reversal_of 冲突 = 原单已被另一事务冲正
            throw new BizException("出仓单 #" + id + " 已被冲正，禁止重复冲正");
        }

        // ④ 原单置 REVERSED 并挂双向链路
        original.setStatus("REVERSED");
        original.setReversedById(rerecord.getId());
        outboundRepository.save(original);

        OutboundCorrection c = new OutboundCorrection();
        c.setChangeType("REVERSAL");
        c.setRecordId(id);
        c.setNewRecordId(rerecord.getId());
        c.setOldPlatformId(fromId);
        c.setNewPlatformId(toId);
        c.setOldCustomer(original.getCustomer());
        c.setNewCustomer(request.getCustomer());
        c.setOldTons(oldTons);
        c.setNewTons(newTons);
        c.setOldPlateNo(original.getPlateNo());
        c.setNewPlateNo(request.getPlateNo());
        c.setOperator(rerecord.getConfirmOperator());
        c.setOperatedAt(LocalDateTime.now());
        c.setRemark(request.getReason());
        correctionRepository.save(c);

        if (fromId.equals(toId)) {
            fieldLogService.write("OUTBOUND", from,
                    "出仓冲正重记（同坨台）：#" + id + " 冲正恢复 " + txt(oldTons)
                            + " 吨，重记新单 #" + rerecord.getId() + " 扣 " + txt(newTons)
                            + " 吨 → " + request.getCustomer() + "（" + plate(request.getPlateNo())
                            + "），库存净变 " + txt(newTons.subtract(oldTons)) + " 吨",
                    c.getOperator());
        } else {
            fieldLogService.write("OUTBOUND", from,
                    "出仓冲正：#" + id + " 恢复 " + txt(oldTons) + " 吨，账面库存回升至 "
                            + txt(from.getStockTons()) + " 吨（重记新单 #" + rerecord.getId() + "）",
                    c.getOperator());
            fieldLogService.write("OUTBOUND", target,
                    "冲正重记入扣：新单 #" + rerecord.getId() + "（冲正自 #" + id + "）扣 "
                            + txt(newTons) + " 吨 → " + request.getCustomer() + "（"
                            + plate(request.getPlateNo()) + "），账面库存减至 "
                            + txt(target.getStockTons()) + " 吨",
                    c.getOperator());
        }
        return toVO(rerecord, target);
    }

    // ---------------- 查询：流水 / 复核队列 / 更正痕迹 ----------------

    /** 单坨台账（全部状态，复核留痕完整可见） */
    @Transactional(readOnly = true)
    public List<OutboundVO> outbounds(Long platformId) {
        mustPlatform(platformId);
        Map<Long, SaltPlatform> platforms = platformMap();
        List<OutboundVO> list = new ArrayList<>();
        for (OutboundRecord r : outboundRepository.findByPlatformIdOrderByOutDateDescIdDesc(platformId)) {
            list.add(toVO(r, platforms));
        }
        return list;
    }

    /** 全部出仓流水（可按状态过滤）；待复核在前的复核队列传 PENDING */
    @Transactional(readOnly = true)
    public List<OutboundVO> allOutbounds(String status) {
        List<OutboundRecord> records = (status == null || status.isBlank())
                ? outboundRepository.findAllByOrderByCreatedAtDescIdDesc()
                : outboundRepository.findByStatusOrderByCreatedAtDescIdDesc(status);
        Map<Long, SaltPlatform> platforms = platformMap();
        List<OutboundVO> list = new ArrayList<>();
        for (OutboundRecord r : records) {
            list.add(toVO(r, platforms));
        }
        return list;
    }

    /** 一张单的全部更正痕迹（车牌可多次直改；冲正至多一条） */
    @Transactional(readOnly = true)
    public List<CorrectionVO> corrections(Long recordId) {
        outboundRepository.findById(recordId)
                .orElseThrow(() -> new BizException("出仓单不存在：id=" + recordId));
        Map<Long, SaltPlatform> platforms = platformMap();
        List<OutboundCorrection> all = new ArrayList<>(
                correctionRepository.findByRecordIdOrderByOperatedAtAscIdAsc(recordId));
        all.addAll(correctionRepository.findByNewRecordIdOrderByOperatedAtAscIdAsc(recordId));
        all.sort(Comparator.comparing(OutboundCorrection::getOperatedAt)
                .thenComparing(OutboundCorrection::getId));
        List<CorrectionVO> list = new ArrayList<>();
        for (OutboundCorrection c : all) {
            list.add(CorrectionVO.of(c, platformText(c.getOldPlatformId(), platforms),
                    platformText(c.getNewPlatformId(), platforms)));
        }
        return list;
    }

    // ---------------- helpers ----------------

    private Map<Long, SaltPlatform> platformMap() {
        Map<Long, SaltPlatform> map = new LinkedHashMap<>();
        platformRepository.findAll().forEach(p -> map.put(p.getId(), p));
        return map;
    }

    private SaltPlatform mustPlatform(Long id) {
        return platformRepository.findById(id)
                .orElseThrow(() -> new BizException("坨台不存在：id=" + id));
    }

    private OutboundVO toVO(OutboundRecord r, Map<Long, SaltPlatform> platforms) {
        SaltPlatform p = platforms.get(r.getPlatformId());
        return toVO(r, p);
    }

    private OutboundVO toVO(OutboundRecord r, SaltPlatform p) {
        return OutboundVO.of(r,
                p == null ? null : p.getPlatformNo(),
                p == null ? null : p.getName(),
                p == null ? null : p.getSaltType());
    }

    private String platformText(Long id, Map<Long, SaltPlatform> platforms) {
        SaltPlatform p = platforms.get(id);
        return p == null ? ("#" + id) : p.getPlatformNo() + " " + p.getName();
    }

    private BigDecimal positiveTons(BigDecimal tons) {
        if (tons == null || tons.signum() <= 0) {
            throw new BizException("出仓吨数必须大于 0");
        }
        return tons;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String txt(BigDecimal v) {
        return nz(v).stripTrailingZeros().toPlainString();
    }

    private String plate(String p) {
        return p == null || p.isBlank() ? "未填车牌" : p;
    }

    private String who(String operator, String fallback) {
        return operator == null || operator.isBlank() ? fallback : operator.trim();
    }
}
