package com.fangjian.clinic.domain;

import com.fangjian.clinic.domain.enums.MemberTxnType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 会员储值流水。 */
@Entity
@Table(name = "member_txn")
@Getter
@Setter
@NoArgsConstructor
public class MemberTxn extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 20)
    private MemberTxnType txnType;

    /** 发生金额（充值/退款为正，消费为负）。 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "ref_type", length = 32)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(length = 200)
    private String remark;
}
