package com.yiguan.his.domain;

import com.yiguan.his.domain.enums.PrescriptionStatus;
import com.yiguan.his.domain.enums.PrescriptionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 处方主表。 */
@Entity
@Table(name = "prescription")
@Getter
@Setter
@NoArgsConstructor
public class Prescription extends BaseEntity {

    @Column(name = "prescription_no", nullable = false, unique = true, length = 32)
    private String prescriptionNo;

    @Column(name = "visit_id", nullable = false)
    private Long visitId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrescriptionType type;

    /** 煎法：代煎/自煎/免煎颗粒。 */
    @Column(name = "decoction_method", length = 50)
    private String decoctionMethod;

    /** 剂数。 */
    @Column(nullable = false)
    private Integer doses = 1;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrescriptionStatus status = PrescriptionStatus.PENDING;

    @Column(length = 200)
    private String remark;

    /** 对应的收费单 id。 */
    @Column(name = "charge_order_id")
    private Long chargeOrderId;
}
