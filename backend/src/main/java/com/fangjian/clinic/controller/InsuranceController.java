package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.service.InsuranceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 医保 / 第三方结算接口（第一版为扩展点）。 */
@RestController
@RequestMapping("/api/insurance")
public class InsuranceController {

    private final InsuranceService insuranceService;

    public InsuranceController(InsuranceService insuranceService) {
        this.insuranceService = insuranceService;
    }

    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> providers() {
        return ApiResponse.ok(insuranceService.providers());
    }

    @PostMapping("/settle")
    public ApiResponse<Map<String, Object>> settle(@RequestBody SettleRequest request) {
        return ApiResponse.ok(insuranceService.settle(request.chargeOrderId(), request.providerCode()));
    }

    @GetMapping("/settlements")
    public ApiResponse<List<Map<String, Object>>> settlements(@RequestParam Long orderId) {
        return ApiResponse.ok(insuranceService.settlements(orderId));
    }

    public record SettleRequest(Long chargeOrderId, String providerCode) {
    }
}
