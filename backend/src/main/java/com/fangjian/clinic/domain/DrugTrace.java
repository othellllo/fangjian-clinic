package com.fangjian.clinic.domain;

import com.fangjian.clinic.domain.enums.TraceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 药品追溯码（一物一码）。
 *
 * <p>合规背景：《医疗保障基金使用监督管理条例实施细则》（国家医保局令第 7 号，2026-04-01 施行）
 * 第九条要求定点医药机构应用药品耗材追溯码并上传；第二十四条规定未按规定使用可处罚。
 * 中药饮片、院内制剂暂不强制采集（国家医保局办公室 2024-09-30 公告）。</p>
 */
@Entity
@Table(name = "drug_trace")
@Getter
@Setter
@NoArgsConstructor
public class DrugTrace extends BaseEntity {

    @Column(name = "drug_id", nullable = false)
    private Long drugId;

    @Column(name = "drug_name", length = 100)
    private String drugName;

    @Column(name = "batch_no", length = 50)
    private String batchNo;

    /** 20 位追溯码，全局唯一。 */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TraceStatus status = TraceStatus.IN_STOCK;

    @Column(name = "prescription_id")
    private Long prescriptionId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "patient_name", length = 50)
    private String patientName;

    @Column(name = "bind_time")
    private LocalDateTime bindTime;

    @Column(name = "dispense_time")
    private LocalDateTime dispenseTime;

    @Column(name = "return_time")
    private LocalDateTime returnTime;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 50)
    private String operatorName;

    /** PENDING 待上传 / UPLOADED 已上传 / UNSUPPORTED 未接入平台 */
    @Column(name = "upload_status", length = 20)
    private String uploadStatus = "PENDING";

    @Column(name = "upload_time")
    private LocalDateTime uploadTime;
}
