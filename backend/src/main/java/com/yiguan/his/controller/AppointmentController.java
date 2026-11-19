package com.yiguan.his.controller;

import com.yiguan.his.common.ApiResponse;
import com.yiguan.his.common.PageResult;
import com.yiguan.his.domain.enums.AppointmentStatus;
import com.yiguan.his.dto.AppointmentDto;
import com.yiguan.his.service.AppointmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 预约（员工侧）：查看与到店报到。 */
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public ApiResponse<PageResult<AppointmentDto>> page(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        LocalDate day = date == null ? LocalDate.now() : date;
        return ApiResponse.ok(appointmentService.page(day, status, doctorId, page, size));
    }

    @GetMapping("/slots")
    public ApiResponse<List<Map<String, Object>>> slots(
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return ApiResponse.ok(appointmentService.slots(doctorId, date));
    }

    @PostMapping("/{id}/arrive")
    public ApiResponse<AppointmentDto> arrive(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.arrive(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<AppointmentDto> cancel(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.cancel(id));
    }
}
