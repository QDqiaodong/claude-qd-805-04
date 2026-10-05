package com.salt.field.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.salt.field.common.FieldDict;

import lombok.Getter;
import lombok.Setter;

/**
 * 池子视图对象（前端平面 SVG 与 chips 条直接用它）
 */
@Getter
@Setter
public class PondVO {

    private Long id;
    private String pondNo;
    private String name;
    private Long areaId;
    private String areaName;
    private String areaType;
    private String areaTypeName;
    private BigDecimal areaMu;
    private String boardStatus;
    private String boardStatusName;
    private BigDecimal brineDegree;
    private BigDecimal brineDepth;
    private String pondStatus;
    private String pondStatusName;
    private Integer gridRow;
    private Integer gridCol;
    private Boolean harvestable;

    // ---- 雨后停工闭环（读时由 RainStopService 按最新雨情装配，刷新页面保持一致） ----
    /** 池子是否处于雨后 24h 窗口内 */
    private Boolean rainActive;
    /** 触发窗口的有效降雨时刻（停工起算时刻） */
    private LocalDateTime rainAt;
    /** 触发窗口的降雨量 mm */
    private BigDecimal rainfall;
    /** 最早可复工时刻（rainAt + 24h） */
    private LocalDateTime rainResumeAt;
    /** 同池当前处于「雨情停工」的作业单数 */
    private Long rainStoppedCount;

    public static PondVO of(Long id,
                            String pondNo,
                            String name,
                            Long areaId,
                            String areaName,
                            String areaType,
                            BigDecimal areaMu,
                            String boardStatus,
                            BigDecimal brineDegree,
                            BigDecimal brineDepth,
                            String pondStatus,
                            Integer gridRow,
                            Integer gridCol) {
        PondVO v = new PondVO();
        v.id = id;
        v.pondNo = pondNo;
        v.name = name;
        v.areaId = areaId;
        v.areaName = areaName;
        v.areaType = areaType;
        v.areaTypeName = FieldDict.areaTypeName(areaType);
        v.areaMu = areaMu;
        v.boardStatus = boardStatus;
        v.boardStatusName = FieldDict.boardStatusName(boardStatus);
        v.brineDegree = brineDegree;
        v.brineDepth = brineDepth;
        v.pondStatus = pondStatus;
        v.pondStatusName = FieldDict.pondStatusName(pondStatus);
        v.gridRow = gridRow;
        v.gridCol = gridCol;
        v.harvestable = !"REPAIR".equals(boardStatus)
                && brineDegree != null
                && brineDegree.compareTo(FieldDict.HARVEST_MIN_BRINE) >= 0;
        return v;
    }
}
