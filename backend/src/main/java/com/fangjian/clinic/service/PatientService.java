package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.common.CodeGenerator;
import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.domain.Patient;
import com.fangjian.clinic.domain.SysUser;
import com.fangjian.clinic.domain.Visit;
import com.fangjian.clinic.domain.enums.Gender;
import com.fangjian.clinic.dto.PatientDto;
import com.fangjian.clinic.dto.VisitDto;
import com.fangjian.clinic.repository.PatientRepository;
import com.fangjian.clinic.repository.SysUserRepository;
import com.fangjian.clinic.repository.VisitRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 患者档案。 */
@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final VisitRepository visitRepository;
    private final SysUserRepository userRepository;

    public PatientService(PatientRepository patientRepository,
                          VisitRepository visitRepository,
                          SysUserRepository userRepository) {
        this.patientRepository = patientRepository;
        this.visitRepository = visitRepository;
        this.userRepository = userRepository;
    }

    public static PatientDto toDto(Patient patient) {
        return new PatientDto(
                patient.getId(),
                patient.getPatientNo(),
                patient.getName(),
                patient.getGender(),
                patient.getBirthDate(),
                VisitAssembler.age(patient),
                patient.getPhone(),
                patient.getIdCard(),
                patient.getAddress(),
                patient.getAllergy(),
                patient.getChronicHistory(),
                patient.getMemberId(),
                patient.getCreatedAt());
    }

    public PageResult<PatientDto> search(String keyword, int page, int size) {
        Page<Patient> result = patientRepository.search(
                keyword == null ? "" : keyword.trim(),
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, PatientService::toDto);
    }

    public PatientDto get(Long id) {
        return toDto(find(id));
    }

    public Patient find(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("患者"));
    }

    @Transactional
    public PatientDto create(String name, Gender gender, LocalDate birthDate, String phone,
                             String idCard, String address, String allergy, String chronicHistory) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("患者姓名不能为空");
        }
        Patient patient = new Patient();
        patient.setPatientNo(CodeGenerator.dateCode("P"));
        patient.setName(name.trim());
        patient.setGender(gender == null ? Gender.MALE : gender);
        patient.setBirthDate(birthDate);
        patient.setPhone(phone);
        patient.setIdCard(idCard);
        patient.setAddress(address);
        patient.setAllergy(allergy);
        patient.setChronicHistory(chronicHistory);
        return toDto(patientRepository.save(patient));
    }

    @Transactional
    public PatientDto update(Long id, String name, Gender gender, LocalDate birthDate, String phone,
                             String idCard, String address, String allergy, String chronicHistory) {
        Patient patient = find(id);
        if (StringUtils.hasText(name)) {
            patient.setName(name.trim());
        }
        if (gender != null) {
            patient.setGender(gender);
        }
        patient.setBirthDate(birthDate);
        patient.setPhone(phone);
        patient.setIdCard(idCard);
        patient.setAddress(address);
        patient.setAllergy(allergy);
        patient.setChronicHistory(chronicHistory);
        return toDto(patientRepository.save(patient));
    }

    public List<VisitDto> visits(Long patientId) {
        find(patientId);
        List<Visit> visits = visitRepository.findByPatientIdOrderByRegisterTimeDesc(patientId);
        Map<Long, SysUser> doctors = new HashMap<>();
        List<VisitDto> result = new ArrayList<>(visits.size());
        for (Visit visit : visits) {
            SysUser doctor = doctors.computeIfAbsent(visit.getDoctorId(),
                    id -> userRepository.findById(id).orElse(null));
            result.add(VisitAssembler.toDto(visit, null, doctor, null));
        }
        return result;
    }

    @Transactional
    public void bindMember(Long patientId, Long memberId) {
        Patient patient = find(patientId);
        patient.setMemberId(memberId);
        patientRepository.save(patient);
    }
}
