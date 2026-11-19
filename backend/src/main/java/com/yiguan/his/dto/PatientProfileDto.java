package com.yiguan.his.dto;

import com.yiguan.his.domain.enums.Gender;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 患者端「我的资料」视图：在患者信息基础上补充会员余额。 */
public record PatientProfileDto(
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
        /** 会员余额；未开卡为 null。 */
        BigDecimal memberBalance,
        Integer memberLevel) {
}
