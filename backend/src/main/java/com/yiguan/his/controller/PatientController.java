package com.yiguan.his.controller;

import com.yiguan.his.common.ApiResponse;
import com.yiguan.his.common.PageResult;
import com.yiguan.his.domain.enums.Gender;
import com.yiguan.his.dto.PatientDto;
import com.yiguan.his.dto.VisitDto;
import com.yiguan.his.service.PatientService;
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

/** 患者档案接口。 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    public ApiResponse<PageResult<PatientDto>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(patientService.search(keyword, page, size));
    }

    @PostMapping
    public ApiResponse<PatientDto> create(@RequestBody PatientRequest request) {
        return ApiResponse.ok(patientService.create(request.name(), request.gender(), request.birthDate(),
                request.phone(), request.idCard(), request.address(), request.allergy(), request.chronicHistory()));
    }

    @GetMapping("/{id}")
    public ApiResponse<PatientDto> get(@PathVariable Long id) {
        return ApiResponse.ok(patientService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<PatientDto> update(@PathVariable Long id, @RequestBody PatientRequest request) {
        return ApiResponse.ok(patientService.update(id, request.name(), request.gender(), request.birthDate(),
                request.phone(), request.idCard(), request.address(), request.allergy(), request.chronicHistory()));
    }

    @GetMapping("/{id}/visits")
    public ApiResponse<List<VisitDto>> visits(@PathVariable Long id) {
        return ApiResponse.ok(patientService.visits(id));
    }

    public record PatientRequest(String name, Gender gender, LocalDate birthDate, String phone,
                                 String idCard, String address, String allergy, String chronicHistory) {
    }
}
