package com.yiguan.his.domain;

import com.yiguan.his.domain.enums.VisitStatus;
import com.yiguan.his.domain.enums.VisitType;
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

/** 一次挂号 = 一次就诊。 */
@Entity
@Table(name = "visit")
@Getter
@Setter
@NoArgsConstructor
public class Visit extends BaseEntity {

    @Column(name = "visit_no", nullable = false, unique = true, length = 32)
    private String visitNo;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitStatus status;

    /** 当日该医生的排队号。 */
    @Column(name = "queue_no")
    private Integer queueNo;

    /** 挂号费。 */
    @Column(name = "register_fee", precision = 10, scale = 2)
    private BigDecimal registerFee = BigDecimal.ZERO;

    @Column(name = "register_time", nullable = false)
    private LocalDateTime registerTime;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 主诉（挂号时录入，可被病历覆盖）。 */
    @Column(length = 500)
    private String complaint;
}
