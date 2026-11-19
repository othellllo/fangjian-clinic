package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.domain.enums.VisitStatus;
import com.fangjian.clinic.domain.enums.VisitType;
import com.fangjian.clinic.dto.MedicalRecordDto;
import com.fangjian.clinic.dto.MedicalRecordRequest;
import com.fangjian.clinic.dto.VisitDto;
import com.fangjian.clinic.service.VisitService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 挂号 / 就诊 / 病历接口。 */
@RestController
@RequestMapping("/api/visits")
public class VisitController {

    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @PostMapping("/register")
    public ApiResponse<VisitDto> register(@RequestBody RegisterRequest request) {
        return ApiResponse.ok(visitService.register(request.patientId(), request.doctorId(),
                request.type(), request.complaint()));
    }

    @GetMapping("/queue")
    public ApiResponse<List<VisitDto>> queue(@RequestParam(required = false) Long doctorId) {
        return ApiResponse.ok(visitService.queue(doctorId));
    }

    @GetMapping
    public ApiResponse<PageResult<VisitDto>> search(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) VisitStatus status,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(visitService.search(date, status, doctorId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(visitService.detail(id));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<VisitDto> start(@PathVariable Long id) {
        return ApiResponse.ok(visitService.start(id));
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<VisitDto> finish(@PathVariable Long id) {
        return ApiResponse.ok(visitService.finish(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<VisitDto> cancel(@PathVariable Long id) {
        return ApiResponse.ok(visitService.cancel(id));
    }

    @GetMapping("/{id}/record")
    public ApiResponse<MedicalRecordDto> getRecord(@PathVariable Long id) {
        return ApiResponse.ok(visitService.getRecord(id));
    }

    @PutMapping("/{id}/record")
    public ApiResponse<MedicalRecordDto> saveRecord(@PathVariable Long id,
                                                    @RequestBody MedicalRecordRequest request) {
        return ApiResponse.ok(visitService.saveRecord(id, request));
    }

    public record RegisterRequest(Long patientId, Long doctorId, VisitType type, String complaint) {
    }
}
