package com.salt.field.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.salt.field.common.FieldDict;

import lombok.Getter;
import lombok.Setter;

/**
 * 左侧池区树节点：池区 → 池号
 */
@Getter
@Setter
public class AreaTreeVO {

    private Long id;
    private String code;
    private String label;
    private String areaType;
    private String areaTypeName;
    private BigDecimal areaMu;
    private String tideGate;
    private String ownerName;
    private Integer bandIndex;
    private List<PondVO> ponds = new ArrayList<>();

    public static AreaTreeVO of(Long id,
                                String code,
                                String name,
                                String areaType,
                                BigDecimal areaMu,
                                String tideGate,
                                String ownerName,
                                Integer bandIndex) {
        AreaTreeVO v = new AreaTreeVO();
        v.id = id;
        v.code = code;
        v.label = name;
        v.areaType = areaType;
        v.areaTypeName = FieldDict.areaTypeName(areaType);
        v.areaMu = areaMu;
        v.tideGate = tideGate;
        v.ownerName = ownerName;
        v.bandIndex = bandIndex;
        return v;
    }
}
