package com.yiguan.his.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 扫码发药记录：每扫一次记一条，用于计算处方发药进度。 */
@Entity
@Table(name = "dispense_scan")
@Getter
@Setter
@NoArgsConstructor
public class DispenseScan extends BaseEntity {

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Column(name = "drug_id", nullable = false)
    private Long drugId;

    @Column(name = "drug_name", length = 100)
    private String drugName;

    /** 扫到的药品条码/编码（追溯码场景为空）。 */
    @Column(length = 64)
    private String barcode;

    /** 核销的追溯码。 */
    @Column(name = "trace_code", length = 64)
    private String traceCode;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 50)
    private String operatorName;
}
