package com.fangjian.clinic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 会员卡（储值/积分）。 */
@Entity
@Table(name = "member_card")
@Getter
@Setter
@NoArgsConstructor
public class Member extends BaseEntity {

    @Column(name = "member_no", nullable = false, unique = true, length = 32)
    private String memberNo;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 20)
    private String phone;

    /** 会员等级：1 普通 / 2 银卡 / 3 金卡。 */
    @Column(nullable = false)
    private Integer level = 1;

    /** 储值余额。 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer points = 0;

    /** 1 正常 / 0 停用。 */
    @Column(nullable = false)
    private Integer status = 1;
}
