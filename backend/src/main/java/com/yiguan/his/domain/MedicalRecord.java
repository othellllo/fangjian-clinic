package com.yiguan.his.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 中医病历，一次就诊一份。 */
@Entity
@Table(name = "medical_record")
@Getter
@Setter
@NoArgsConstructor
public class MedicalRecord extends BaseEntity {

    @Column(name = "visit_id", nullable = false, unique = true)
    private Long visitId;

    @Column(name = "chief_complaint", length = 1000)
    private String chiefComplaint;

    @Column(name = "present_illness", length = 1000)
    private String presentIllness;

    @Column(name = "past_history", length = 1000)
    private String pastHistory;

    /** 望诊。 */
    @Column(length = 1000)
    private String inspection;

    /** 闻诊。 */
    @Column(length = 1000)
    private String auscultation;

    /** 问诊。 */
    @Column(length = 1000)
    private String inquiry;

    /** 切诊。 */
    @Column(length = 1000)
    private String palpation;

    /** 舌象。 */
    @Column(length = 500)
    private String tongue;

    /** 脉象。 */
    @Column(length = 500)
    private String pulse;

    /** 辨证（证型）。 */
    @Column(length = 500)
    private String syndrome;

    /** 治则治法。 */
    @Column(name = "treatment_principle", length = 500)
    private String treatmentPrinciple;

    /** 医嘱。 */
    @Column(length = 1000)
    private String advice;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 20)
    private com.yiguan.his.domain.enums.RecordType recordType =
            com.yiguan.his.domain.enums.RecordType.TCM;

    /** 中医病名（如 胃痞、不寐）。 */
    @Column(name = "tcm_disease", length = 200)
    private String tcmDisease;

    /** 西医体格检查。 */
    @Column(name = "physical_exam", length = 2000)
    private String physicalExam;

    /** 西医辅助检查。 */
    @Column(name = "auxiliary_exam", length = 1000)
    private String auxiliaryExam;

    /** 西医诊断。 */
    @Column(name = "western_diagnosis", length = 500)
    private String westernDiagnosis;

    /** ICD-10 编码。 */
    @Column(name = "icd_code", length = 20)
    private String icdCode;

    /** 病历版本号，每次保存 +1，用于追溯。 */
    @Column(nullable = false)
    private Integer version = 1;
}
