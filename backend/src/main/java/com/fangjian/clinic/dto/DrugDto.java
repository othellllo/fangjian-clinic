package com.fangjian.clinic.dto;

import com.fangjian.clinic.domain.enums.DrugCategory;
import com.fangjian.clinic.domain.enums.PregnancyLevel;
import com.fangjian.clinic.domain.enums.SpecialHandling;
import com.fangjian.clinic.domain.enums.ToxicityLevel;

import java.math.BigDecimal;

/** 药品视图：字典 + 库存 + 临床属性（煎法/剂量/妊娠/毒性）。 */
public record DrugDto(
        Long id,
        String code,
        String name,
        String pinyin,
        DrugCategory category,
        String spec,
        String unit,
        BigDecimal price,
        BigDecimal costPrice,
        BigDecimal stockWarn,
        BigDecimal stockMax,
        BigDecimal stockQty,
        Integer needTrace,
        SpecialHandling specialHandling,
        BigDecimal doseMin,
        BigDecimal doseMax,
        PregnancyLevel pregnancyLevel,
        ToxicityLevel toxicityLevel,
        String functions,
        Integer status) {
}
