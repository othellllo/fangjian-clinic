package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.domain.enums.PolicyCategory;
import com.fangjian.clinic.domain.enums.PolicyStatus;
import com.fangjian.clinic.dto.PolicyDto;
import com.fangjian.clinic.service.PolicyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 医保政策库接口。 */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public ApiResponse<PageResult<PolicyDto>> page(
            @RequestParam(required = false) PolicyCategory category,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(policyService.page(category, status, keyword, page, size));
    }

    @GetMapping("/reminders")
    public ApiResponse<Map<String, Object>> reminders() {
        return ApiResponse.ok(policyService.reminders());
    }

    @GetMapping("/{id}")
    public ApiResponse<PolicyDto> get(@PathVariable Long id) {
        return ApiResponse.ok(policyService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PolicyDto> create(@RequestBody PolicyService.PolicyRequest request) {
        return ApiResponse.ok(policyService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PolicyDto> update(@PathVariable Long id, @RequestBody PolicyService.PolicyRequest request) {
        return ApiResponse.ok(policyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        policyService.delete(id);
        return ApiResponse.ok();
    }
}
