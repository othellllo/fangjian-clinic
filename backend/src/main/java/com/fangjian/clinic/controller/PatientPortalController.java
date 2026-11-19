package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.dto.AppointmentDto;
import com.fangjian.clinic.dto.MemberDto;
import com.fangjian.clinic.dto.PatientProfileDto;
import com.fangjian.clinic.dto.VisitDto;
import com.fangjian.clinic.service.AppointmentService;
import com.fangjian.clinic.service.PatientAuthService;
import com.fangjian.clinic.service.PatientService;
import com.fangjian.clinic.service.UserService;
import com.fangjian.clinic.service.VisitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 患者端接口（微信小程序）。
 * 使用独立患者 token（角色 PATIENT），只能访问自己的数据。
 */
@RestController
@RequestMapping("/api/patient")
public class PatientPortalController {

    private final PatientAuthService patientAuthService;
    private final AppointmentService appointmentService;
    private final VisitService visitService;
    private final UserService userService;

    public PatientPortalController(PatientAuthService patientAuthService,
                                   AppointmentService appointmentService,
                                   VisitService visitService,
                                   UserService userService) {
        this.patientAuthService = patientAuthService;
        this.appointmentService = appointmentService;
        this.visitService = visitService;
        this.userService = userService;
    }

    @PostMapping("/auth/login")
    public ApiResponse<PatientAuthService.LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(patientAuthService.login(request.phone(), request.code()));
    }

    @GetMapping("/profile")
    public ApiResponse<PatientProfileDto> profile() {
        return ApiResponse.ok(patientAuthService.profile());
    }

    @GetMapping("/doctors")
    public ApiResponse<List<MemberDto.User>> doctors() {
        return ApiResponse.ok(userService.doctors());
    }

    @GetMapping("/slots")
    public ApiResponse<List<Map<String, Object>>> slots(
            @RequestParam Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return ApiResponse.ok(appointmentService.slots(doctorId, date));
    }

    @GetMapping("/appointments")
    public ApiResponse<List<AppointmentDto>> appointments() {
        return ApiResponse.ok(appointmentService.listForPatient(
                patientAuthService.currentPatient().getId()));
    }

    @PostMapping("/appointments")
    public ApiResponse<AppointmentDto> createAppointment(@RequestBody CreateAppointmentRequest request) {
        Long patientId = patientAuthService.currentPatient().getId();
        return ApiResponse.ok(appointmentService.create(patientId, request.doctorId(), request.appointDate(),
                request.timeSlot(), request.complaint(), "MINIAPP"));
    }

    @PostMapping("/appointments/{id}/cancel")
    public ApiResponse<AppointmentDto> cancelAppointment(@PathVariable Long id) {
        Long patientId = patientAuthService.currentPatient().getId();
        var appointment = appointmentService.find(id);
        if (!appointment.getPatientId().equals(patientId)) {
            throw BusinessException.forbidden("无权操作该预约");
        }
        return ApiResponse.ok(appointmentService.cancel(id));
    }

    @GetMapping("/visits")
    public ApiResponse<PageResult<VisitDto>> visits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(visitService.patientVisits(
                patientAuthService.currentPatient().getId(), page, size));
    }

    @GetMapping("/visits/{id}")
    public ApiResponse<Map<String, Object>> visitDetail(@PathVariable Long id) {
        return ApiResponse.ok(visitService.patientVisitDetail(
                patientAuthService.currentPatient().getId(), id));
    }

    public record LoginRequest(@NotBlank(message = "手机号不能为空") String phone, String code) {
    }

    public record CreateAppointmentRequest(Long doctorId, LocalDate appointDate, String timeSlot, String complaint) {
    }
}
