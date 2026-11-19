package com.fangjian.clinic.dto;

import com.fangjian.clinic.domain.enums.PrescriptionStatus;
import com.fangjian.clinic.domain.enums.PrescriptionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 处方视图。 */
public record PrescriptionDto(
        Long id,
        String prescriptionNo,
        Long visitId,
        String patientName,
        String doctorName,
        PrescriptionType type,
        String decoctionMethod,
        Integer doses,
        BigDecimal totalAmount,
        PrescriptionStatus status,
        String remark,
        LocalDateTime createdAt,
        Long chargeOrderId,
        List<Item> items) {

    public record Item(
            Long id,
            Long drugId,
            String drugName,
            String spec,
            String unit,
            BigDecimal dose,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal amount,
            String usageText) {
    }
}
