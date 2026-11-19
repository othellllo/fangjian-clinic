package com.yiguan.his.service;

import com.yiguan.his.common.BusinessException;
import com.yiguan.his.domain.Member;
import com.yiguan.his.domain.Patient;
import com.yiguan.his.domain.enums.UserRole;
import com.yiguan.his.dto.PatientDto;
import com.yiguan.his.dto.PatientProfileDto;
import com.yiguan.his.repository.MemberRepository;
import com.yiguan.his.repository.PatientRepository;
import com.yiguan.his.security.JwtService;
import com.yiguan.his.security.LoginUser;
import com.yiguan.his.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 患者端（微信小程序）登录。
 *
 * <p>第一版不接短信服务商：开发/演示环境验证码固定为配置项
 * {@code app.patient.login-code}（默认 123456），接入短信后替换为真实校验即可。</p>
 */
@Service
public class PatientAuthService {

    private final PatientRepository patientRepository;
    private final MemberRepository memberRepository;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final String loginCode;

    public PatientAuthService(PatientRepository patientRepository,
                              MemberRepository memberRepository,
                              JwtService jwtService,
                              AuditService auditService,
                              @Value("${app.patient.login-code:123456}") String loginCode) {
        this.patientRepository = patientRepository;
        this.memberRepository = memberRepository;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.loginCode = loginCode;
    }

    public LoginResult login(String phone, String code) {
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException("请输入手机号");
        }
        if (!StringUtils.hasText(code) || !code.equals(loginCode)) {
            auditService.recordAnonymous(phone, "认证", "患者登录失败", "FAIL", "验证码错误");
            throw new BusinessException("验证码错误");
        }
        Patient patient = patientRepository.findFirstByPhoneOrderByIdAsc(phone.trim())
                .orElseThrow(() -> new BusinessException("该手机号尚未在医馆建档，请先到前台建档"));
        LoginUser loginUser = new LoginUser(patient.getId(), patient.getPhone(), patient.getName(),
                UserRole.PATIENT);
        auditService.recordAnonymous(phone, "认证", "患者登录成功", "SUCCESS", patient.getPatientNo());
        return new LoginResult(jwtService.generate(loginUser), PatientService.toDto(patient));
    }

    public Patient currentPatient() {
        Long patientId = SecurityUtils.currentUserId();
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(401, "患者不存在，请重新登录"));
    }

    /** 患者端「我的」页资料，带会员余额，避免患者端再去请求员工接口。 */
    public PatientProfileDto profile() {
        Patient patient = currentPatient();
        Member member = patient.getId() == null ? null
                : memberRepository.findByPatientId(patient.getId()).orElse(null);
        return new PatientProfileDto(
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
                member == null ? null : member.getBalance(),
                member == null ? null : member.getLevel());
    }

    public record LoginResult(String token, PatientDto patient) {
    }
}
