package com.fangjian.clinic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 医保/第三方结算记录。 */
@Entity
@Table(name = "insurance_settlement")
@Getter
@Setter
@NoArgsConstructor
public class InsuranceSettlement extends BaseEntity {

    @Column(name = "settlement_no", nullable = false, unique = true, length = 32)
    private String settlementNo;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 地区实现标识，如 noop / shanghai / zhejiang。 */
    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;

    /** SUCCESS / FAILED / UNSUPPORTED。 */
    @Column(nullable = false, length = 20)
    private String status;

    /** 自费金额。 */
    @Column(name = "self_pay_amount", precision = 12, scale = 2)
    private BigDecimal selfPayAmount = BigDecimal.ZERO;

    /** 医保支付金额。 */
    @Column(name = "insurance_amount", precision = 12, scale = 2)
    private BigDecimal insuranceAmount = BigDecimal.ZERO;

    @Column(length = 500)
    private String message;
}
