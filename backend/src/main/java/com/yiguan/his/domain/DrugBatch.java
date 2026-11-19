package com.yiguan.his.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 药品批次（按批号 + 效期管理库存）。 */
@Entity
@Table(name = "drug_batch")
@Getter
@Setter
@NoArgsConstructor
public class DrugBatch extends BaseEntity {

    @Column(name = "drug_id", nullable = false)
    private Long drugId;

    @Column(name = "batch_no", nullable = false, length = 50)
    private String batchNo;

    @Column(name = "production_date")
    private LocalDate productionDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** 该批次当前结存数量。 */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;
}
