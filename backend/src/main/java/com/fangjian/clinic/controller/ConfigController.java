package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.service.ConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 医馆基础信息接口。 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/clinic")
    public ApiResponse<Map<String, String>> clinic() {
        return ApiResponse.ok(configService.clinicInfo());
    }

    @PutMapping("/clinic")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, String>> updateClinic(@RequestBody Map<String, String> values) {
        configService.updateClinicInfo(values);
        return ApiResponse.ok(configService.clinicInfo());
    }
}
