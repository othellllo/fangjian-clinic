package com.yiguan.his.dto;

import com.yiguan.his.domain.enums.Gender;
import com.yiguan.his.domain.enums.VisitStatus;
import com.yiguan.his.domain.enums.VisitType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 就诊视图。 */
public record VisitDto(
        Long id,
        String visitNo,
        Integer queueNo,
        Long patientId,
        String patientName,
        Gender patientGender,
        Integer patientAge,
        Long doctorId,
        String doctorName,
        VisitType type,
        VisitStatus status,
        BigDecimal registerFee,
        LocalDateTime registerTime,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String complaint,
        Long chargeOrderId) {
}
