package com.yiguan.his.controller;

import com.yiguan.his.common.ApiResponse;
import com.yiguan.his.service.PharmacyScanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 扫码发药 / 扫码出库接口。 */
@RestController
@RequestMapping("/api/pharmacy")
public class PharmacyScanController {

    private final PharmacyScanService scanService;

    public PharmacyScanController(PharmacyScanService scanService) {
        this.scanService = scanService;
    }

    /** 扫码发药：扫一次扣一次库存，扫完自动完成发药。 */
    @PostMapping("/scan")
    public ApiResponse<Map<String, Object>> scan(@RequestBody ScanRequest request) {
        return ApiResponse.ok(scanService.scan(request.prescriptionId(), request.code(),
                request.quantity(), request.traceCode()));
    }

    /** 处方扫码进度。 */
    @GetMapping("/scan/progress/{prescriptionId}")
    public ApiResponse<List<Map<String, Object>>> progress(@PathVariable Long prescriptionId) {
        return ApiResponse.ok(scanService.progress(prescriptionId));
    }

    /** 无处方直接出库（领用、报损），必须填原因。 */
    @PostMapping("/scan-out")
    public ApiResponse<Map<String, Object>> scanOut(@RequestBody ScanOutRequest request) {
        return ApiResponse.ok(scanService.scanOut(request.code(), request.quantity(), request.reason()));
    }

    public record ScanRequest(Long prescriptionId, String code, BigDecimal quantity, String traceCode) {
    }

    public record ScanOutRequest(String code, BigDecimal quantity, String reason) {
    }
}
