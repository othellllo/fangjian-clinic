package com.yiguan.his.service;

import com.yiguan.his.common.BusinessException;
import com.yiguan.his.common.CodeGenerator;
import com.yiguan.his.common.PageResult;
import com.yiguan.his.domain.Appointment;
import com.yiguan.his.domain.Patient;
import com.yiguan.his.domain.SysUser;
import com.yiguan.his.domain.enums.AppointmentStatus;
import com.yiguan.his.domain.enums.UserRole;
import com.yiguan.his.domain.enums.VisitType;
import com.yiguan.his.dto.AppointmentDto;
import com.yiguan.his.repository.AppointmentRepository;
import com.yiguan.his.repository.PatientRepository;
import com.yiguan.his.repository.SysUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 预约挂号：小程序预约 → 到店报到生成就诊记录。 */
@Service
public class AppointmentService {

    /** 可预约时段。 */
    public static final List<String> TIME_SLOTS = List.of(
            "08:30-09:00", "09:00-09:30", "09:30-10:00", "10:00-10:30",
            "10:30-11:00", "11:00-11:30",
            "14:00-14:30", "14:30-15:00", "15:00-15:30", "15:30-16:00",
            "16:00-16:30", "16:30-17:00");

    /** 每个时段可约人数。 */
    public static final int SLOT_CAPACITY = 5;

    private final AppointmentRepository repository;
    private final PatientRepository patientRepository;
    private final SysUserRepository userRepository;
    private final VisitService visitService;

    public AppointmentService(AppointmentRepository repository,
                              PatientRepository patientRepository,
                              SysUserRepository userRepository,
                              VisitService visitService) {
        this.repository = repository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.visitService = visitService;
    }

    public PageResult<AppointmentDto> page(LocalDate date, AppointmentStatus status, Long doctorId,
                                           int page, int size) {
        Page<Appointment> result = repository.search(date, status, doctorId,
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, AppointmentService::toDto);
    }

    public List<AppointmentDto> listForPatient(Long patientId) {
        return repository.findByPatientIdOrderByAppointDateDescTimeSlotAsc(patientId).stream()
                .map(AppointmentService::toDto).toList();
    }

    public List<Map<String, Object>> slots(Long doctorId, LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String slot : TIME_SLOTS) {
            long booked = doctorId == null ? 0
                    : repository.countByDoctorIdAndAppointDateAndTimeSlotAndStatusNot(
                    doctorId, day, slot, AppointmentStatus.CANCELLED);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timeSlot", slot);
            row.put("booked", booked);
            row.put("capacity", SLOT_CAPACITY);
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public AppointmentDto create(Long patientId, Long doctorId, LocalDate appointDate,
                                 String timeSlot, String complaint, String source) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> BusinessException.notFound("患者"));
        SysUser doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> BusinessException.notFound("医生"));
        if (doctor.getRole() != UserRole.DOCTOR && doctor.getRole() != UserRole.ADMIN) {
            throw new BusinessException("请选择医生进行预约");
        }
        if (appointDate == null || appointDate.isBefore(LocalDate.now())) {
            throw new BusinessException("预约日期不能早于今天");
        }
        if (!TIME_SLOTS.contains(timeSlot)) {
            throw new BusinessException("请选择有效的预约时段");
        }
        if (repository.existsByPatientIdAndDoctorIdAndAppointDateAndTimeSlotAndStatusNot(
                patientId, doctorId, appointDate, timeSlot, AppointmentStatus.CANCELLED)) {
            throw new BusinessException("该时段已为同一患者预约过，请勿重复提交");
        }
        long booked = repository.countByDoctorIdAndAppointDateAndTimeSlotAndStatusNot(
                doctorId, appointDate, timeSlot, AppointmentStatus.CANCELLED);
        if (booked >= SLOT_CAPACITY) {
            throw new BusinessException("该时段号源已满，请选择其他时段");
        }

        Appointment appointment = new Appointment();
        appointment.setAppointNo(CodeGenerator.timeCode("A"));
        appointment.setPatientId(patientId);
        appointment.setPatientName(patient.getName());
        appointment.setPatientPhone(patient.getPhone());
        appointment.setDoctorId(doctorId);
        appointment.setDoctorName(doctor.getRealName());
        appointment.setAppointDate(appointDate);
        appointment.setTimeSlot(timeSlot);
        appointment.setComplaint(complaint);
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment.setSource(StringUtils.hasText(source) ? source : "MINIAPP");
        return toDto(repository.save(appointment));
    }

    /** 到店报到：生成候诊中的就诊记录。 */
    @Transactional
    public AppointmentDto arrive(Long id) {
        Appointment appointment = find(id);
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new BusinessException("只有「已预约」状态才能到店报到");
        }
        if (appointment.getVisitId() != null) {
            throw new BusinessException("该预约已生成就诊记录");
        }
        var visit = visitService.register(appointment.getPatientId(), appointment.getDoctorId(),
                VisitType.FIRST, appointment.getComplaint());
        appointment.setVisitId(visit.id());
        appointment.setStatus(AppointmentStatus.ARRIVED);
        return toDto(repository.save(appointment));
    }

    @Transactional
    public AppointmentDto cancel(Long id) {
        Appointment appointment = find(id);
        if (appointment.getStatus() == AppointmentStatus.ARRIVED) {
            throw new BusinessException("已到店的预约不能取消");
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessException("预约已取消");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        return toDto(repository.save(appointment));
    }

    public Appointment find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("预约"));
    }

    static AppointmentDto toDto(Appointment entity) {
        return new AppointmentDto(entity.getId(), entity.getAppointNo(), entity.getPatientId(),
                entity.getPatientName(), entity.getPatientPhone(), entity.getDoctorId(), entity.getDoctorName(),
                entity.getAppointDate(), entity.getTimeSlot(), entity.getComplaint(), entity.getStatus(),
                entity.getVisitId(), entity.getSource(), entity.getCreatedAt());
    }
}
