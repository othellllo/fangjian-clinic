package com.yiguan.his.dto;

import com.yiguan.his.domain.enums.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 预约视图。 */
public record AppointmentDto(
        Long id,
        String appointNo,
        Long patientId,
        String patientName,
        String patientPhone,
        Long doctorId,
        String doctorName,
        LocalDate appointDate,
        String timeSlot,
        String complaint,
        AppointmentStatus status,
        Long visitId,
        String source,
        LocalDateTime createdAt) {
}
