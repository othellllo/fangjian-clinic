package com.yiguan.his.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 处方明细。 */
@Entity
@Table(name = "prescription_item")
@Getter
@Setter
@NoArgsConstructor
public class PrescriptionItem extends BaseEntity {

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Column(name = "drug_id", nullable = false)
    private Long drugId;

    /** 药名快照，避免药品字典改名后历史处方失真。 */
    @Column(name = "drug_name", nullable = false, length = 100)
    private String drugName;

    @Column(length = 100)
    private String spec;

    @Column(length = 20)
    private String unit;

    /** 单次剂量（如 10g）。 */
    @Column(precision = 12, scale = 3)
    private BigDecimal dose;

    /** 数量（发药总量）。 */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    /** 用法：水煎服、日一剂分两次温服 等。 */
    @Column(name = "usage_text", length = 200)
    private String usageText;
}
