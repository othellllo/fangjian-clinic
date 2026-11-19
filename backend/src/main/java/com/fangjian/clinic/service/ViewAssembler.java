package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.domain.Patient;
import com.fangjian.clinic.domain.SysUser;
import com.fangjian.clinic.domain.Visit;
import com.fangjian.clinic.domain.enums.ChargeType;
import com.fangjian.clinic.domain.enums.VisitStatus;
import com.fangjian.clinic.repository.ChargeOrderRepository;
import com.fangjian.clinic.repository.PatientRepository;
import com.fangjian.clinic.repository.SysUserRepository;
import com.fangjian.clinic.repository.VisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 通用装配辅助：避免各服务重复拼装视图。 */
@Service
public class ViewAssembler {

    private final PatientRepository patientRepository;
    private final SysUserRepository userRepository;
    private final ChargeOrderRepository chargeOrderRepository;

    public ViewAssembler(PatientRepository patientRepository,
                         SysUserRepository userRepository,
                         ChargeOrderRepository chargeOrderRepository) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.chargeOrderRepository = chargeOrderRepository;
    }

    public Patient patient(Long id) {
        return patientRepository.findById(id).orElse(null);
    }

    public SysUser user(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public String userName(Long id) {
        SysUser user = user(id);
        return user == null ? null : user.getRealName();
    }

    public String patientName(Long id) {
        Patient patient = patient(id);
        return patient == null ? null : patient.getName();
    }

    public Long registerChargeOrderId(Long visitId) {
        return chargeOrderRepository.findFirstByVisitIdAndType(visitId, ChargeType.REGISTER)
                .map(order -> order.getId())
                .orElse(null);
    }

    /**
     * 批量装配就诊视图，预先加载患者与医生，避免 N+1 查询。
     */
    public List<com.fangjian.clinic.dto.VisitDto> toVisitDtos(List<Visit> visits) {
        Map<Long, Patient> patients = new HashMap<>();
        Map<Long, SysUser> doctors = new HashMap<>();
        List<com.fangjian.clinic.dto.VisitDto> result = new ArrayList<>(visits.size());
        for (Visit visit : visits) {
            Patient patient = patients.computeIfAbsent(visit.getPatientId(), this::patient);
            SysUser doctor = doctors.computeIfAbsent(visit.getDoctorId(), this::user);
            result.add(VisitAssembler.toDto(visit, patient, doctor, registerChargeOrderId(visit.getId())));
        }
        return result;
    }

    public static com.fangjian.clinic.dto.VisitDto toDto(Visit visit, Patient patient, SysUser doctor, Long chargeOrderId) {
        return VisitAssembler.toDto(visit, patient, doctor, chargeOrderId);
    }

    /** 挂号当日统计用的时间边界。 */
    public static LocalDate dayOf(Visit visit) {
        return visit.getRegisterTime().toLocalDate();
    }

    public static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    static VisitStatus requireStatus(Visit visit, VisitStatus expected, String action) {
        if (visit.getStatus() != expected) {
            throw new BusinessException("当前就诊状态不允许" + action);
        }
        return visit.getStatus();
    }
}
