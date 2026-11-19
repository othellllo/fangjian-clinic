package com.fangjian.clinic.dto;

import com.fangjian.clinic.domain.enums.RecordType;

/** 病历保存请求（中医/西医共用，按 recordType 取用不同字段）。 */
public record MedicalRecordRequest(
        RecordType recordType,
        String chiefComplaint,
        String presentIllness,
        String pastHistory,
        /* 中医四诊 */
        String inspection,
        String auscultation,
        String inquiry,
        String palpation,
        String tongue,
        String pulse,
        /* 中医诊断 */
        String tcmDisease,
        String syndrome,
        String treatmentPrinciple,
        /* 西医 */
        String physicalExam,
        String auxiliaryExam,
        String westernDiagnosis,
        String icdCode,
        String advice) {
}
