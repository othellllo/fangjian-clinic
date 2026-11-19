package com.yiguan.his.dto;

import com.yiguan.his.domain.enums.DecoctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 代煎单视图。 */
public record DecoctionDto(
        Long id,
        String decoctionNo,
        Long prescriptionId,
        String prescriptionNo,
        Long patientId,
        String patientName,
        String doctorName,
        Integer doses,
        DecoctionStatus status,
        String decoctionMethod,
        LocalDateTime expectedTime,
        LocalDateTime cookStartTime,
        LocalDateTime cookEndTime,
        LocalDateTime deliverTime,
        String deliverType,
        String phone,
        String remark,
        LocalDateTime createdAt,
        List<Item> items) {

    public record Item(String drugName, BigDecimal dose, BigDecimal quantity, String unit) {
    }
}
