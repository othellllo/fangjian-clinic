package com.yiguan.his.domain;

import com.yiguan.his.domain.enums.StockChangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 库存流水（只增不改，结存靠流水推导）。 */
@Entity
@Table(name = "stock_ledger")
@Getter
@Setter
@NoArgsConstructor
public class StockLedger extends BaseEntity {

    @Column(name = "drug_id", nullable = false)
    private Long drugId;

    @Column(name = "drug_name", length = 100)
    private String drugName;

    @Column(name = "batch_no", length = 50)
    private String batchNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private StockChangeType changeType;

    /** 变动数量（正数入库，负数出库）。 */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "before_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal beforeQty;

    @Column(name = "after_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal afterQty;

    /** 关联业务类型：PRESCRIPTION / MANUAL / ... */
    @Column(name = "ref_type", length = 32)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 50)
    private String operatorName;

    @Column(length = 200)
    private String remark;
}
