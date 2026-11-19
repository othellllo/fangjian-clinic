package com.fangjian.clinic.domain;

import com.fangjian.clinic.domain.enums.PayMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 支付记录。一次收费可能分多笔支付。 */
@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayMethod method;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** 第三方交易号（微信/支付宝/医保返回）。 */
    @Column(name = "trade_no", length = 64)
    private String tradeNo;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 50)
    private String operatorName;

    @Column(length = 200)
    private String remark;
}
