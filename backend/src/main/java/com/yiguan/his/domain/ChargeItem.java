package com.yiguan.his.domain;

import com.yiguan.his.domain.enums.ChargeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 收费单明细。 */
@Entity
@Table(name = "charge_item")
@Getter
@Setter
@NoArgsConstructor
public class ChargeItem extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ChargeType itemType;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "drug_id")
    private Long drugId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    /** 业务来源 id，如处方 id。 */
    @Column(name = "ref_id")
    private Long refId;
}
