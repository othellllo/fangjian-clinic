package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.dto.PrescriptionDto;
import com.fangjian.clinic.dto.QcResult;
import com.fangjian.clinic.dto.TraceDto;
import com.fangjian.clinic.service.PrescriptionCheckService;
import com.fangjian.clinic.service.PrescriptionService;
import com.fangjian.clinic.service.TraceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 处方接口。 */
@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final TraceService traceService;
    private final PrescriptionCheckService prescriptionCheckService;

    public PrescriptionController(PrescriptionService prescriptionService,
                                  TraceService traceService,
                                  PrescriptionCheckService prescriptionCheckService) {
        this.prescriptionService = prescriptionService;
        this.traceService = traceService;
        this.prescriptionCheckService = prescriptionCheckService;
    }

    /** 该处方已核销的追溯码（与 /api/traces/prescription/{id} 等价，保留两种路径）。 */
    @GetMapping("/{id}/traces")
    public ApiResponse<List<TraceDto>> traces(@PathVariable Long id) {
        return ApiResponse.ok(traceService.listByPrescription(id));
    }

    /** 开方前预检：配伍禁忌、妊娠禁忌、毒性、剂量、特殊煎法标注。 */
    @PostMapping("/check")
    public ApiResponse<QcResult.PrescriptionCheck> check(@RequestBody CheckRequest request) {
        return ApiResponse.ok(prescriptionCheckService.check(request.items(), request.pregnant()));
    }

    @PostMapping
    public ApiResponse<PrescriptionDto> create(@RequestBody PrescriptionService.CreateRequest request) {
        return ApiResponse.ok(prescriptionService.create(request));
    }

    @GetMapping("/pending-dispense")
    public ApiResponse<List<PrescriptionDto>> pendingDispense() {
        return ApiResponse.ok(prescriptionService.pendingDispense());
    }

    @GetMapping("/{id}")
    public ApiResponse<PrescriptionDto> detail(@PathVariable Long id) {
        return ApiResponse.ok(prescriptionService.detail(id));
    }

    @GetMapping
    public ApiResponse<List<PrescriptionDto>> listByVisit(@RequestParam Long visitId) {
        return ApiResponse.ok(prescriptionService.listByVisit(visitId));
    }

    @PostMapping("/{id}/dispense")
    public ApiResponse<PrescriptionDto> dispense(@PathVariable Long id,
                                                @RequestBody(required = false) DispenseRequest request) {
        return ApiResponse.ok(prescriptionService.dispense(id,
                request == null || request.traces() == null ? List.of() : request.traces()));
    }

    /** 发药请求：需追溯药品必须提供追溯码。 */
    public record DispenseRequest(List<PrescriptionService.TraceInput> traces) {
    }

    /** 预检请求。 */
    public record CheckRequest(List<QcResult.DoseUsage> items, Boolean pregnant) {
    }
}
