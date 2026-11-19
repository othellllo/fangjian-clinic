package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.domain.enums.DrugCategory;
import com.fangjian.clinic.service.SettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 管理设置接口（打印、收款方式、药品库存上下限）。 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/print")
    public ApiResponse<Map<String, Object>> printSettings() {
        return ApiResponse.ok(settingsService.printSettings());
    }

    @PutMapping("/print")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> updatePrintSettings(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(settingsService.updatePrintSettings(body));
    }

    @GetMapping("/payment-methods")
    public ApiResponse<List<Map<String, Object>>> paymentMethods() {
        return ApiResponse.ok(settingsService.paymentMethods());
    }

    @PutMapping("/payment-methods")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> updatePaymentMethods(
            @RequestBody List<Map<String, Object>> methods) {
        return ApiResponse.ok(settingsService.updatePaymentMethods(methods));
    }

    @PutMapping("/drug-limits/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> batchDrugLimits(@RequestBody DrugLimitRequest request) {
        return ApiResponse.ok(settingsService.batchDrugLimits(request.category(), request.stockWarn(), request.stockMax()));
    }

    public record DrugLimitRequest(DrugCategory category, BigDecimal stockWarn, BigDecimal stockMax) {
    }
}
