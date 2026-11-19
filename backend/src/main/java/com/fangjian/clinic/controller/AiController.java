package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.dto.QcResult;
import com.fangjian.clinic.service.AiService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** AI 助写接口。 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/settings")
    public ApiResponse<Map<String, Object>> settings() {
        return ApiResponse.ok(aiService.settings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiService.updateSettings(body));
    }

    @PostMapping("/assist")
    public ApiResponse<QcResult.AiResult> assist(@RequestBody AssistRequest request) {
        return ApiResponse.ok(aiService.assist(request.field(), request.recordType(), request.context()));
    }

    public record AssistRequest(String field, String recordType, Map<String, Object> context) {
    }
}
