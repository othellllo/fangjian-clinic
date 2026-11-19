package com.yiguan.his.domain;

import com.yiguan.his.domain.enums.DecoctionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 代煎单：中药处方发药后进入煎药房流程。 */
@Entity
@Table(name = "decoction_order")
@Getter
@Setter
@NoArgsConstructor
public class DecoctionOrder extends BaseEntity {

    @Column(name = "decoction_no", nullable = false, unique = true, length = 32)
    private String decoctionNo;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Column(name = "prescription_no", length = 32)
    private String prescriptionNo;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** 患者姓名快照。 */
    @Column(name = "patient_name", length = 50)
    private String patientName;

    @Column(name = "doctor_id")
    private Long doctorId;

    @Column(name = "doctor_name", length = 50)
    private String doctorName;

    @Column(nullable = false)
    private Integer doses = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecoctionStatus status = DecoctionStatus.PENDING;

    @Column(name = "cook_start_time")
    private LocalDateTime cookStartTime;

    @Column(name = "cook_end_time")
    private LocalDateTime cookEndTime;

    /** 预计取药时间。 */
    @Column(name = "expected_time")
    private LocalDateTime expectedTime;

    @Column(name = "deliver_time")
    private LocalDateTime deliverTime;

    /** SELF 自取 / DELIVERY 配送。 */
    @Column(name = "deliver_type", length = 20)
    private String deliverType;

    @Column(length = 200)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(length = 500)
    private String remark;
}
