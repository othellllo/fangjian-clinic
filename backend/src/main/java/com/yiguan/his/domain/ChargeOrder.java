package com.yiguan.his.domain;

import com.yiguan.his.domain.enums.ChargeStatus;
import com.yiguan.his.domain.enums.ChargeType;
import com.yiguan.his.domain.enums.PayMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 收费单。 */
@Entity
@Table(name = "charge_order")
@Getter
@Setter
@NoArgsConstructor
public class ChargeOrder extends BaseEntity {

    @Column(name = "order_no", nullable = false, unique = true, length = 32)
    private String orderNo;

    @Column(name = "visit_id")
    private Long visitId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeType type;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "payable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal payableAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeStatus status = ChargeStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_method", length = 20)
    private PayMethod payMethod;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(length = 200)
    private String remark;
}
