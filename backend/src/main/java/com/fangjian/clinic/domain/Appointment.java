package com.fangjian.clinic.domain;

import com.fangjian.clinic.domain.enums.AppointmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 预约挂号（来源：微信小程序 / 前台）。 */
@Entity
@Table(name = "appointment")
@Getter
@Setter
@NoArgsConstructor
public class Appointment extends BaseEntity {

    @Column(name = "appoint_no", nullable = false, unique = true, length = 32)
    private String appointNo;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** 患者姓名快照，避免列表查询联表。 */
    @Column(name = "patient_name", length = 50)
    private String patientName;

    @Column(name = "patient_phone", length = 20)
    private String patientPhone;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "doctor_name", length = 50)
    private String doctorName;

    @Column(name = "appoint_date", nullable = false)
    private LocalDate appointDate;

    @Column(name = "time_slot", nullable = false, length = 20)
    private String timeSlot;

    @Column(length = 500)
    private String complaint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    /** 到店报到后生成的就诊记录 id。 */
    @Column(name = "visit_id")
    private Long visitId;

    /** MINIAPP / DESK */
    @Column(nullable = false, length = 20)
    private String source = "MINIAPP";
}
