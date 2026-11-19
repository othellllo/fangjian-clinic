package com.yiguan.his.dto;

import com.yiguan.his.domain.enums.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 患者视图。 */
public record PatientDto(
        Long id,
        String patientNo,
        String name,
        Gender gender,
        LocalDate birthDate,
        Integer age,
        String phone,
        String idCard,
        String address,
        String allergy,
        String chronicHistory,
        Long memberId,
        LocalDateTime createdAt) {
}
