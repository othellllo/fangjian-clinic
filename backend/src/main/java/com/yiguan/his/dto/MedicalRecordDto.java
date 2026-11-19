package com.yiguan.his.dto;

import com.yiguan.his.domain.enums.RecordType;

import java.time.LocalDateTime;

/** 中医病历视图。 */
public record MedicalRecordDto(
        Long id,
        Long visitId,
        RecordType recordType,
        String chiefComplaint,
        String presentIllness,
        String pastHistory,
        String inspection,
        String auscultation,
        String inquiry,
        String palpation,
        String tongue,
        String pulse,
        String tcmDisease,
        String syndrome,
        String treatmentPrinciple,
        String physicalExam,
        String auxiliaryExam,
        String westernDiagnosis,
        String icdCode,
        String advice,
        Integer version,
        LocalDateTime updatedAt) {
}
