package com.yiguan.his.domain;

import com.yiguan.his.domain.enums.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 患者档案。 */
@Entity
@Table(name = "patient")
@Getter
@Setter
@NoArgsConstructor
public class Patient extends BaseEntity {

    @Column(name = "patient_no", nullable = false, unique = true, length = 32)
    private String patientNo;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 20)
    private String phone;

    @Column(name = "id_card", length = 32)
    private String idCard;

    @Column(length = 200)
    private String address;

    /** 过敏史。 */
    @Column(length = 500)
    private String allergy;

    /** 既往史/慢病。 */
    @Column(name = "chronic_history", length = 500)
    private String chronicHistory;

    /** 妊娠状态：0 否 / 1 是 / 2 未知。 */
    @Column(nullable = false)
    private Integer pregnant = 2;

    /** 关联会员 id，可为空。 */
    @Column(name = "member_id")
    private Long memberId;
}
