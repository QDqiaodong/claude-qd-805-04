package com.salt.field.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 派工任务生命周期规则（唯一事实来源）。
 *
 * <p><b>合法状态机：</b>TODO 待派 → DOING 进行中 → DONE 已完成，只许逐级前进。
 * <ul>
 *   <li>重复提交同一目标（当前已是目标态）= 幂等成功，不重复留痕；</li>
 *   <li>跳级（TODO→DONE）、倒退（DOING→TODO、DONE→任意）、未知取值、已完成后的任何修改，
 *       一律拒绝并给出可操作的原因（当前是什么、允许做什么、请刷新）。</li>
 * </ul>
 *
 * <p><b>历史数据口径（列表 / 详情 / 操作结果三处一致）：</b>
 * <ul>
 *   <li>{@link #canonical(String)} 能命中的<b>别名</b>（如 DO、RUNNING、已派、进行中）：
 *       归一化到最接近的合法状态后继续可用，原始值由启动对账写入流转留痕；</li>
 *   <li><b>空值与无法识别的非法值</b>：{@code null} 是唯一的「异常」展示键，原始值经
 *       {@code abnormalRaw} 原样保留在列表 / 详情里，禁止继续流转（保留审计真相），
 *       只能走留痕的「修正异常」入口修复。</li>
 * </ul>
 */
public final class DispatchLifecycle {

    private DispatchLifecycle() {
    }

    public static final String TODO = "TODO";
    public static final String DOING = "DOING";
    public static final String DONE = "DONE";

    /** 异常行在列表 / 详情 / 操作结果中统一使用的展示键（不是合法业务状态） */
    public static final String ABNORMAL = null;

    /** 唯一合法的前进步：TODO→DOING、DOING→DONE */
    private static final Map<String, String> NEXT = Map.of(TODO, DOING, DOING, DONE);

    /** 历史别名 → 合法状态（键已 trim + 大写；中文别名见 {@link #canonical(String)}） */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("TO_BE_DISPATCHED", TODO);
        ALIASES.put("PENDING", TODO);
        ALIASES.put("WAIT", TODO);
        ALIASES.put("WAITING", TODO);
        ALIASES.put("NEW", TODO);
        ALIASES.put("CREATED", TODO);
        ALIASES.put("待派", TODO);
        ALIASES.put("待派工", TODO);
        ALIASES.put("未派", TODO);

        ALIASES.put("DO", DOING);
        ALIASES.put("IN_PROGRESS", DOING);
        ALIASES.put("RUNNING", DOING);
        ALIASES.put("PROCESSING", DOING);
        ALIASES.put("ONGOING", DOING);
        ALIASES.put("WORKING", DOING);
        ALIASES.put("STARTED", DOING);
        ALIASES.put("DISPATCHED", DOING);
        ALIASES.put("进行中", DOING);
        ALIASES.put("已派", DOING);
        ALIASES.put("已派工", DOING);
        ALIASES.put("作业中", DOING);

        ALIASES.put("FINISHED", DONE);
        ALIASES.put("COMPLETE", DONE);
        ALIASES.put("COMPLETED", DONE);
        ALIASES.put("CLOSED", DONE);
        ALIASES.put("END", DONE);
        ALIASES.put("FINISH", DONE);
        ALIASES.put("已完成", DONE);
        ALIASES.put("完成", DONE);
        ALIASES.put("完工", DONE);
        ALIASES.put("完结", DONE);
    }

    /** 合法状态（{@code null} 不是合法状态） */
    public static boolean isLegal(String status) {
        return TODO.equals(status) || DOING.equals(status) || DONE.equals(status);
    }

    /**
     * 归一化：合法值原样返回；已知别名（trim + 大写后匹配）映射到最接近的合法状态；
     * 空值 / 未知非法值返回 {@code null}（异常，禁止流转）。
     */
    public static String canonical(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        String upper = v.toUpperCase(java.util.Locale.ROOT);
        if (isLegal(upper)) {
            return upper;
        }
        return ALIASES.get(upper);
    }

    /** 是否为「可归一化的旧别名」（区别于空值 / 非法值：别名直接可用，另两类只能修复） */
    public static boolean isAlias(String raw) {
        if (raw == null) {
            return false;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return false;
        }
        String upper = v.toUpperCase(java.util.Locale.ROOT);
        return !isLegal(upper) && ALIASES.containsKey(upper);
    }

    /** 当前状态唯一合法的下一步；{@code null}（异常）或 DONE（终态）没有合法下一步 */
    public static String nextOf(String canonicalStatus) {
        return NEXT.get(canonicalStatus);
    }

    /** 目标值是否就是当前态（重复提交保持幂等） */
    public static boolean isSame(String canonicalStatus, String target) {
        return canonicalStatus != null && canonicalStatus.equals(target);
    }

    /** 是否唯一合法前进一步 */
    public static boolean isAdvance(String canonicalStatus, String target) {
        return NEXT.get(canonicalStatus) != null && NEXT.get(canonicalStatus).equals(target);
    }

    /** 非法流转 / 已完成后修改 / 异常行的可操作拒绝原因（供操作结果与 409 复用） */
    public static String rejectReason(String currentCanonical, String rawStatus, String target) {
        if (currentCanonical == null) {
            return "该任务状态为历史异常值「" + rawLabel(rawStatus)
                    + "」，不在待派/进行中/已完成生命周期内，已禁止继续流转以保留审计真相；"
                    + "请由调度主管在「修正异常」中核实后改入合法状态，再继续操作";
        }
        if (DONE.equals(currentCanonical)) {
            return "任务已完成并锁定，完成后的任何修改都不允许（不能退回进行中/待派）；"
                    + "如现场确有返工需要，请新建派工任务";
        }
        String legalTarget = target == null ? "" : target.trim();
        if (!isLegal(legalTarget)) {
            return "目标状态取值有误：" + rawLabel(target)
                    + "（只接受 TODO 待派 / DOING 进行中 / DONE 已完成）；当前为「"
                    + dispatchStatusName(currentCanonical) + "」，唯一可执行的下一步是「"
                    + dispatchStatusName(nextOf(currentCanonical)) + "」";
        }
        if (legalTarget.equals(currentCanonical)) {
            return ""; // 幂等，不应走到这里
        }
        if (TODO.equals(legalTarget)) {
            return "不允许退回「待派」：派工生命周期只能前进（待派→进行中→已完成）；当前为「"
                    + dispatchStatusName(currentCanonical) + "」，唯一可执行的下一步是「"
                    + dispatchStatusName(nextOf(currentCanonical)) + "」";
        }
        if (TODO.equals(currentCanonical) && DONE.equals(legalTarget)) {
            return "不允许从「待派」直接跳级到「已完成」，须先派工进入「进行中」，完成后再置「已完成」";
        }
        if (DOING.equals(currentCanonical) && TODO.equals(legalTarget)) {
            return "不允许从「进行中」退回「待派」，只能前进到「已完成」";
        }
        return "非法状态流转：当前「" + dispatchStatusName(currentCanonical) + "」不能改为「"
                + dispatchStatusName(legalTarget) + "」；唯一可执行的下一步是「"
                + dispatchStatusName(nextOf(currentCanonical)) + "」";
    }

    /** 并发冲突（另一调度员已抢先推进 / 旧页面提交）的可操作原因 */
    public static String conflictReason(String expectedCanonical, String currentCanonical, String rawStatus) {
        String now = currentCanonical == null ? "历史异常值「" + rawLabel(rawStatus) + "」"
                : "「" + dispatchStatusName(currentCanonical) + "」";
        String held = expectedCanonical == null ? "未知状态" : "「" + dispatchStatusName(expectedCanonical) + "」";
        return "任务状态已被另一名调度员改动：您页面上停留的是" + held + "，当前最新为" + now
                + "。已完成结果不会被覆盖，本次提交未生效；请刷新列表查看最新状态与生效时间后再操作";
    }

    public static String dispatchStatusName(String canonical) {
        if (TODO.equals(canonical)) {
            return "待派";
        }
        if (DOING.equals(canonical)) {
            return "进行中";
        }
        if (DONE.equals(canonical)) {
            return "已完成";
        }
        return "状态异常";
    }

    /** 异常行统一提示（列表角标 / 详情 / 操作结果一致） */
    public static String abnormalHint(String rawStatus) {
        return "历史异常状态「" + rawLabel(rawStatus) + "」：不在待派/进行中/已完成生命周期内，"
                + "已冻结流转并原样保留；请由调度主管「修正异常」核实后改入合法状态";
    }

    private static String rawLabel(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "空值";
        }
        return raw.trim();
    }
}
