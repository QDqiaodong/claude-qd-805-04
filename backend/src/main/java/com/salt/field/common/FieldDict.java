package com.salt.field.common;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 盐场业务词典与规则阈值（收盐门槛、雨后禁收窗口、池板/状态字典等）。
 * 规则参数集中在此，便于调整或扩展。
 */
public final class FieldDict {

    private FieldDict() {
    }

    /** 收盐卤度门槛 °Bé */
    public static final BigDecimal HARVEST_MIN_BRINE = BigDecimal.valueOf(25);

    /** 雨后禁止收盐的窗口（小时） */
    public static final long RAIN_BLOCK_HOURS = 24L;

    /** 结晶起始卤度 °Bé（低于此值算晒卤） */
    public static final BigDecimal CRYST_MIN_BRINE = BigDecimal.valueOf(20);

    /** 当日计划收盐吨数（进度环分母） */
    public static final BigDecimal DAILY_PLAN_TONS = BigDecimal.valueOf(320);

    /** 池区类型 */
    private static final Map<String, String> AREA_TYPE = Map.of(
            "EVAP", "蒸发池区",
            "REG", "调节池区",
            "CRYST", "结晶池区");

    /** 池板状况 */
    private static final Map<String, String> BOARD_STATUS = Map.of(
            "GOOD", "完好",
            "LEAK", "渗漏",
            "REPAIR", "需修");

    /** 池子作业状态 */
    private static final Map<String, String> POND_STATUS = Map.of(
            "SUN", "晒卤",
            "CRYST", "结晶",
            "WAIT", "待收",
            "REPAIR", "修池");

    /** 盐种 */
    private static final Map<String, String> SALT_TYPE = Map.of(
            "INDUSTRIAL", "工业盐",
            "SUN", "日晒盐",
            "REFINED", "精制盐");

    /** 作业方式 */
    private static final Map<String, String> WORK_MODE = Map.of(
            "MACHINE", "机械",
            "MANUAL", "人工");

    /** 作业状态（RAIN_STOP 由系统在雨后自动设置，人工不能直接改入/改出） */
    private static final Map<String, String> TASK_STATUS = Map.of(
            "PLANNED", "待作业",
            "WORKING", "作业中",
            "RAIN_STOP", "雨情停工",
            "DONE", "已完成",
            "CANCELED", "已取消");

    /** 设备状态 */
    private static final Map<String, String> EQUIP_STATUS = Map.of(
            "RUNNING", "在用",
            "IDLE", "闲置",
            "FAULT", "故障");

    /** 记录类型 */
    private static final Map<String, String> LOG_TYPE = Map.of(
            "BRINE", "测卤",
            "HARVEST", "收盐",
            "OUTBOUND", "出仓",
            "REPAIR", "修池",
            "INSPECT", "巡检");

    /** 调度任务类型 */
    private static final Map<String, String> DISPATCH_TYPE = Map.of(
            "DREDGE", "清淤",
            "FIX", "修池",
            "TRANSFER", "转卤",
            "UPKEEP", "设备保养");

    /** 调度任务状态 */
    private static final Map<String, String> DISPATCH_STATUS = Map.of(
            "TODO", "待派",
            "DOING", "进行中",
            "DONE", "已完成");

    /** 多态目标类型 */
    private static final Map<String, String> TARGET_TYPE = Map.of(
            "POND", "池子",
            "PLATFORM", "坨台",
            "EQUIPMENT", "设备");

    /** 出仓单状态（登记预占 → 复核确认实扣 / 作废释放；已确认可冲正，不可删除） */
    private static final Map<String, String> OUTBOUND_STATUS = Map.of(
            "PENDING", "待复核",
            "CONFIRMED", "已确认",
            "REJECTED", "已作废",
            "REVERSED", "已冲正");

    /** 出仓更正类型 */
    private static final Map<String, String> OUTBOUND_CHANGE = Map.of(
            "PLATE", "车牌直改",
            "REVERSAL", "冲正重记");

    public static String areaTypeName(String v) {
        return AREA_TYPE.getOrDefault(v, v);
    }

    public static String boardStatusName(String v) {
        return BOARD_STATUS.getOrDefault(v, v);
    }

    public static String pondStatusName(String v) {
        return POND_STATUS.getOrDefault(v, v);
    }

    public static String saltTypeName(String v) {
        return SALT_TYPE.getOrDefault(v, v);
    }

    public static String workModeName(String v) {
        return WORK_MODE.getOrDefault(v, v);
    }

    public static String taskStatusName(String v) {
        return TASK_STATUS.getOrDefault(v, v);
    }

    public static String equipStatusName(String v) {
        return EQUIP_STATUS.getOrDefault(v, v);
    }

    public static String logTypeName(String v) {
        return LOG_TYPE.getOrDefault(v, v);
    }

    public static String dispatchTypeName(String v) {
        return DISPATCH_TYPE.getOrDefault(v, v);
    }

    public static String dispatchStatusName(String v) {
        return DISPATCH_STATUS.getOrDefault(v, v);
    }

    public static String targetTypeName(String v) {
        return TARGET_TYPE.getOrDefault(v, v);
    }

    public static String outboundStatusName(String v) {
        return OUTBOUND_STATUS.getOrDefault(v, v);
    }

    public static String outboundChangeName(String v) {
        return OUTBOUND_CHANGE.getOrDefault(v, v);
    }

    public static boolean isValidOutboundStatus(String v) {
        return v != null && OUTBOUND_STATUS.containsKey(v);
    }

    public static boolean isValidTaskStatus(String v) {
        return v != null && TASK_STATUS.containsKey(v);
    }

    /** 人工改状态接口允许直接写入的状态；雨情停工只能由系统进入、由复工确认退出 */
    public static boolean isManualAssignableTaskStatus(String v) {
        return "PLANNED".equals(v) || "WORKING".equals(v) || "DONE".equals(v) || "CANCELED".equals(v);
    }

    /** 尚未完成、可被降雨立即停工的作业状态 */
    public static boolean isOpenTaskStatus(String v) {
        return "PLANNED".equals(v) || "WORKING".equals(v);
    }

    public static boolean isValidBoardStatus(String v) {
        return v != null && BOARD_STATUS.containsKey(v);
    }

    public static boolean isValidPondStatus(String v) {
        return v != null && POND_STATUS.containsKey(v);
    }

    public static boolean isValidTargetType(String v) {
        return v != null && TARGET_TYPE.containsKey(v);
    }
}
