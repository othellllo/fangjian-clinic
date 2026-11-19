package com.yiguan.his.service;

import com.yiguan.his.domain.Patient;
import com.yiguan.his.domain.SysUser;
import com.yiguan.his.domain.Visit;
import com.yiguan.his.dto.VisitDto;

import java.time.LocalDate;
import java.time.Period;

/** 就诊视图装配（患者、医生信息联表拼装）。 */
public final class VisitAssembler {

    private VisitAssembler() {
    }

    public static Integer age(Patient patient) {
        if (patient == null || patient.getBirthDate() == null) {
            return null;
        }
        return Period.between(patient.getBirthDate(), LocalDate.now()).getYears();
    }

    public static VisitDto toDto(Visit visit, Patient patient, SysUser doctor, Long chargeOrderId) {
        return new VisitDto(
                visit.getId(),
                visit.getVisitNo(),
                visit.getQueueNo(),
                visit.getPatientId(),
                patient == null ? null : patient.getName(),
                patient == null ? null : patient.getGender(),
                age(patient),
                visit.getDoctorId(),
                doctor == null ? null : doctor.getRealName(),
                visit.getType(),
                visit.getStatus(),
                visit.getRegisterFee(),
                visit.getRegisterTime(),
                visit.getStartTime(),
                visit.getEndTime(),
                visit.getComplaint(),
                chargeOrderId);
    }
}
