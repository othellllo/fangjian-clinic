package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.domain.enums.TermCategory;
import com.fangjian.clinic.dto.MedicalRecordRequest;
import com.fangjian.clinic.dto.QcResult;
import com.fangjian.clinic.service.RecordQcService;
import com.fangjian.clinic.service.TermService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 病历辅助：术语联想、质控预检。 */
@RestController
@RequestMapping("/api/tcm")
public class TcmAssistController {

    private final TermService termService;
    private final RecordQcService qcService;

    public TcmAssistController(TermService termService, RecordQcService qcService) {
        this.termService = termService;
        this.qcService = qcService;
    }

    @GetMapping("/suggest")
    public ApiResponse<List<QcResult.Suggestion>> suggest(
            @RequestParam TermCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(termService.suggest(category, keyword, limit));
    }

    /** 常见项：光标进入空输入框时展示（历史高频 + 标准术语）。 */
    @GetMapping("/common")
    public ApiResponse<List<QcResult.Suggestion>> common(
            @RequestParam TermCategory category,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(termService.common(category, limit));
    }

    @PostMapping("/qc")
    public ApiResponse<QcResult> qc(@RequestBody MedicalRecordRequest request) {
        return ApiResponse.ok(qcService.check(request));
    }
}
