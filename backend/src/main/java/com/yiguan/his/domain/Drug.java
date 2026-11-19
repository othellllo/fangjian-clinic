package com.yiguan.his.domain;

import com.yiguan.his.domain.enums.DrugCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 药品/饮片字典。 */
@Entity
@Table(name = "drug")
@Getter
@Setter
@NoArgsConstructor
public class Drug extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** 药品条形码（扫码枪扫的码），为空时用编码匹配。 */
    @Column(length = 64)
    private String barcode;

    /** 拼音助记码，便于快速检索。 */
    @Column(length = 50)
    private String pinyin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DrugCategory category;

    /** 规格，如 "10g/袋"、"0.25g*24片"。 */
    @Column(length = 100)
    private String spec;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    /** 低库存预警值。 */
    @Column(name = "stock_warn", precision = 12, scale = 3)
    private BigDecimal stockWarn = BigDecimal.ZERO;

    /** 库存上限（0 表示不限制）。 */
    @Column(name = "stock_max", precision = 12, scale = 3)
    private BigDecimal stockMax = BigDecimal.ZERO;

    /** 是否需采集追溯码：1 需要（中成药/西药/耗材），0 不需要（中药饮片等）。 */
    @Column(name = "need_trace", nullable = false)
    private Integer needTrace = 0;

    /** 饮片特殊煎法。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "special_handling", nullable = false, length = 20)
    private com.yiguan.his.domain.enums.SpecialHandling specialHandling =
            com.yiguan.his.domain.enums.SpecialHandling.NORMAL;

    /** 单次剂量下限（药典常用量，克）。 */
    @Column(name = "dose_min", precision = 10, scale = 2)
    private BigDecimal doseMin;

    /** 单次剂量上限。 */
    @Column(name = "dose_max", precision = 10, scale = 2)
    private BigDecimal doseMax;

    /** 妊娠用药风险。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "pregnancy_level", nullable = false, length = 20)
    private com.yiguan.his.domain.enums.PregnancyLevel pregnancyLevel =
            com.yiguan.his.domain.enums.PregnancyLevel.NONE;

    /** 毒性级别。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "toxicity_level", nullable = false, length = 20)
    private com.yiguan.his.domain.enums.ToxicityLevel toxicityLevel =
            com.yiguan.his.domain.enums.ToxicityLevel.NONE;

    /** 功效主治，供联想与处方提示。 */
    @Column(length = 300)
    private String functions;

    /** 1 启用 / 0 停用。 */
    @Column(nullable = false)
    private Integer status = 1;
}
