package com.yiguan.his.controller;

import com.yiguan.his.common.ApiResponse;
import com.yiguan.his.domain.enums.TermCategory;
import com.yiguan.his.dto.MedicalRecordRequest;
import com.yiguan.his.dto.QcResult;
import com.yiguan.his.service.RecordQcService;
import com.yiguan.his.service.TermService;
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

    @PostMapping("/qc")
    public ApiResponse<QcResult> qc(@RequestBody MedicalRecordRequest request) {
        return ApiResponse.ok(qcService.check(request));
    }
}
