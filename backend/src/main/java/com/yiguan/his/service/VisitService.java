package com.yiguan.his.service;

import com.yiguan.his.common.BusinessException;
import com.yiguan.his.common.CodeGenerator;
import com.yiguan.his.common.PageResult;
import com.yiguan.his.domain.MedicalRecord;
import com.yiguan.his.domain.Patient;
import com.yiguan.his.domain.SysUser;
import com.yiguan.his.domain.Visit;
import com.yiguan.his.domain.enums.ChargeType;
import com.yiguan.his.domain.enums.UserRole;
import com.yiguan.his.domain.enums.VisitStatus;
import com.yiguan.his.domain.enums.VisitType;
import com.yiguan.his.dto.ChargeOrderDto;
import com.yiguan.his.dto.MedicalRecordDto;
import com.yiguan.his.dto.MedicalRecordRequest;
import com.yiguan.his.dto.PatientDto;
import com.yiguan.his.dto.PrescriptionDto;
import com.yiguan.his.dto.VisitDto;
import com.yiguan.his.repository.ChargeOrderRepository;
import com.yiguan.his.repository.MedicalRecordRepository;
import com.yiguan.his.repository.PatientRepository;
import com.yiguan.his.repository.SysUserRepository;
import com.yiguan.his.repository.VisitRepository;
import com.yiguan.his.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 挂号、候诊、接诊与病历。 */
@Service
public class VisitService {

    private final VisitRepository visitRepository;
    private final MedicalRecordRepository recordRepository;
    private final PatientRepository patientRepository;
    private final SysUserRepository userRepository;
    private final ChargeOrderRepository chargeOrderRepository;
    private final ChargeService chargeService;
    private final PrescriptionService prescriptionService;
    private final ConfigService configService;
    private final ViewAssembler viewAssembler;
    private final RecordQcService recordQcService;

    public VisitService(VisitRepository visitRepository,
                        MedicalRecordRepository recordRepository,
                        PatientRepository patientRepository,
                        SysUserRepository userRepository,
                        ChargeOrderRepository chargeOrderRepository,
                        ChargeService chargeService,
                        PrescriptionService prescriptionService,
                        ConfigService configService,
                        ViewAssembler viewAssembler,
                        RecordQcService recordQcService) {
        this.visitRepository = visitRepository;
        this.recordRepository = recordRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.chargeOrderRepository = chargeOrderRepository;
        this.chargeService = chargeService;
        this.prescriptionService = prescriptionService;
        this.configService = configService;
        this.viewAssembler = viewAssembler;
        this.recordQcService = recordQcService;
    }

    @Transactional
    public VisitDto register(Long patientId, Long doctorId, VisitType type, String complaint) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> BusinessException.notFound("患者"));
        SysUser doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> BusinessException.notFound("医生"));
        if (doctor.getRole() != UserRole.DOCTOR && doctor.getRole() != UserRole.ADMIN) {
            throw new BusinessException("请选择医生进行挂号");
        }
        if (doctor.getStatus() == null || doctor.getStatus() != 1) {
            throw new BusinessException("该医生当前停诊");
        }

        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        Integer maxQueueNo = visitRepository.findMaxQueueNo(doctorId, dayStart, dayEnd);

        Visit visit = new Visit();
        visit.setVisitNo(CodeGenerator.timeCode("V"));
        visit.setPatientId(patientId);
        visit.setDoctorId(doctorId);
        visit.setType(type == null ? VisitType.FIRST : type);
        visit.setStatus(VisitStatus.WAITING);
        visit.setQueueNo(maxQueueNo == null ? 1 : maxQueueNo + 1);
        visit.setRegisterTime(LocalDateTime.now());
        visit.setComplaint(complaint);

        BigDecimal fee = configService.getDecimal(ConfigService.KEY_REGISTER_FEE, new BigDecimal("20.00"));
        visit.setRegisterFee(fee);
        visit = visitRepository.save(visit);

        var order = chargeService.createRegisterOrder(visit, patient, fee);
        return VisitAssembler.toDto(visit, patient, doctor, order.getId());
    }

    public List<VisitDto> queue(Long doctorId) {
        Long targetDoctor = doctorId != null ? doctorId : SecurityUtils.currentUserId();
        List<Visit> visits = visitRepository.findByDoctorIdAndStatusInOrderByRegisterTimeAsc(
                targetDoctor, List.of(VisitStatus.WAITING, VisitStatus.IN_SERVICE));
        return viewAssembler.toVisitDtos(visits);
    }

    public PageResult<VisitDto> search(LocalDate date, VisitStatus status, Long doctorId, int page, int size) {
        LocalDateTime from = date == null ? null : date.atStartOfDay();
        LocalDateTime to = date == null ? null : date.plusDays(1).atStartOfDay();
        Page<Visit> result = visitRepository.search(doctorId, status, from, to,
                PageRequest.of(Math.max(page - 1, 0), size));
        List<VisitDto> list = viewAssembler.toVisitDtos(result.getContent());
        return PageResult.of(list, result.getTotalElements(), page, size);
    }

    public Visit find(Long id) {
        return visitRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("就诊记录"));
    }

    /** 患者端：我的就诊记录。 */
    public PageResult<VisitDto> patientVisits(Long patientId, int page, int size) {
        Page<Visit> result = visitRepository.findByPatientIdOrderByRegisterTimeDesc(patientId,
                PageRequest.of(Math.max(page - 1, 0), size));
        List<VisitDto> list = viewAssembler.toVisitDtos(result.getContent());
        return PageResult.of(list, result.getTotalElements(), page, size);
    }

    /** 患者端：就诊详情，校验归属，避免越权查看他人病历。 */
    public Map<String, Object> patientVisitDetail(Long patientId, Long visitId) {
        Visit visit = find(visitId);
        if (!visit.getPatientId().equals(patientId)) {
            throw BusinessException.forbidden("无权查看该就诊记录");
        }
        return detail(visitId);
    }

    public Map<String, Object> detail(Long id) {
        Visit visit = find(id);
        Patient patient = patientRepository.findById(visit.getPatientId()).orElse(null);
        SysUser doctor = userRepository.findById(visit.getDoctorId()).orElse(null);
        Long registerOrderId = viewAssembler.registerChargeOrderId(visit.getId());

        List<PrescriptionDto> prescriptions = prescriptionService.listByVisit(id);
        List<ChargeOrderDto> charges = chargeOrderRepository.findByVisitIdOrderByIdDesc(id)
                .stream().map(order -> chargeService.detail(order.getId())).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("visit", VisitAssembler.toDto(visit, patient, doctor, registerOrderId));
        data.put("patient", patient == null ? null : PatientService.toDto(patient));
        data.put("record", recordRepository.findByVisitId(id).map(VisitService::toRecordDto).orElse(null));
        data.put("prescriptions", prescriptions);
        data.put("charges", charges);
        return data;
    }

    @Transactional
    public VisitDto start(Long id) {
        Visit visit = find(id);
        if (visit.getStatus() != VisitStatus.WAITING) {
            throw new BusinessException("只有候诊中的患者才能接诊");
        }
        visit.setStatus(VisitStatus.IN_SERVICE);
        visit.setStartTime(LocalDateTime.now());
        visitRepository.save(visit);
        return ViewAssembler.toDto(visit, viewAssembler.patient(visit.getPatientId()),
                viewAssembler.user(visit.getDoctorId()), viewAssembler.registerChargeOrderId(id));
    }

    @Transactional
    public VisitDto finish(Long id) {
        Visit visit = find(id);
        if (visit.getStatus() != VisitStatus.IN_SERVICE) {
            throw new BusinessException("只有就诊中的记录才能完成");
        }
        visit.setStatus(VisitStatus.FINISHED);
        visit.setEndTime(LocalDateTime.now());
        visitRepository.save(visit);
        return ViewAssembler.toDto(visit, viewAssembler.patient(visit.getPatientId()),
                viewAssembler.user(visit.getDoctorId()), viewAssembler.registerChargeOrderId(id));
    }

    @Transactional
    public VisitDto cancel(Long id) {
        Visit visit = find(id);
        if (visit.getStatus() == VisitStatus.FINISHED) {
            throw new BusinessException("已完成的就诊不能作废");
        }
        visit.setStatus(VisitStatus.CANCELLED);
        visitRepository.save(visit);
        chargeOrderRepository.findByVisitIdOrderByIdDesc(id).stream()
                .filter(order -> order.getType() == ChargeType.REGISTER)
                .findFirst()
                .ifPresent(order -> chargeService.cancelUnpaidOrder(order.getId()));
        return ViewAssembler.toDto(visit, viewAssembler.patient(visit.getPatientId()),
                viewAssembler.user(visit.getDoctorId()), viewAssembler.registerChargeOrderId(id));
    }

    @Transactional
    public MedicalRecordDto saveRecord(Long visitId, MedicalRecordRequest request) {
        Visit visit = find(visitId);
        if (visit.getStatus() == VisitStatus.CANCELLED) {
            throw new BusinessException("已作废的就诊不能书写病历");
        }
        // 病历质控：BLOCK 级问题不允许保存（中医病历书写基本规范要求的必填项）
        var qc = recordQcService.check(request);
        var blockers = qc.issues().stream()
                .filter(item -> item.level() == com.yiguan.his.domain.enums.IssueLevel.BLOCK)
                .map(item -> item.message() + "（" + item.advice() + "）")
                .toList();
        if (!blockers.isEmpty()) {
            throw new BusinessException("病历必填项未完成：" + String.join("；", blockers));
        }

        MedicalRecord record = recordRepository.findByVisitId(visitId).orElseGet(() -> {
            MedicalRecord created = new MedicalRecord();
            created.setVisitId(visitId);
            created.setVersion(0);
            return created;
        });
        record.setRecordType(request.recordType() == null
                ? com.yiguan.his.domain.enums.RecordType.TCM : request.recordType());
        record.setChiefComplaint(request.chiefComplaint());
        record.setPresentIllness(request.presentIllness());
        record.setPastHistory(request.pastHistory());
        record.setInspection(request.inspection());
        record.setAuscultation(request.auscultation());
        record.setInquiry(request.inquiry());
        record.setPalpation(request.palpation());
        record.setTongue(request.tongue());
        record.setPulse(request.pulse());
        record.setTcmDisease(request.tcmDisease());
        record.setSyndrome(request.syndrome());
        record.setTreatmentPrinciple(request.treatmentPrinciple());
        record.setPhysicalExam(request.physicalExam());
        record.setAuxiliaryExam(request.auxiliaryExam());
        record.setWesternDiagnosis(request.westernDiagnosis());
        record.setIcdCode(request.icdCode());
        record.setAdvice(request.advice());
        record.setVersion(record.getVersion() == null ? 1 : record.getVersion() + 1);

        // 主诉同步回就诊记录，方便列表展示
        if (StringUtils.hasText(request.chiefComplaint())) {
            visit.setComplaint(request.chiefComplaint());
            visitRepository.save(visit);
        }
        return toRecordDto(recordRepository.save(record));
    }

    public MedicalRecordDto getRecord(Long visitId) {
        return recordRepository.findByVisitId(visitId).map(VisitService::toRecordDto).orElse(null);
    }

    static MedicalRecordDto toRecordDto(MedicalRecord record) {
        return new MedicalRecordDto(
                record.getId(), record.getVisitId(), record.getRecordType(), record.getChiefComplaint(),
                record.getPresentIllness(), record.getPastHistory(), record.getInspection(),
                record.getAuscultation(), record.getInquiry(), record.getPalpation(), record.getTongue(),
                record.getPulse(), record.getTcmDisease(), record.getSyndrome(), record.getTreatmentPrinciple(),
                record.getPhysicalExam(), record.getAuxiliaryExam(), record.getWesternDiagnosis(),
                record.getIcdCode(), record.getAdvice(), record.getVersion(), record.getUpdatedAt());
    }
}
