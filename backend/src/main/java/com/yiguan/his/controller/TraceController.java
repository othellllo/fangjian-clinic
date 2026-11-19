package com.yiguan.his.controller;

import com.yiguan.his.common.ApiResponse;
import com.yiguan.his.common.PageResult;
import com.yiguan.his.domain.enums.TraceStatus;
import com.yiguan.his.dto.TraceDto;
import com.yiguan.his.service.TraceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 药品追溯码接口。 */
@RestController
@RequestMapping("/api/traces")
public class TraceController {

    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    @PostMapping("/bind")
    public ApiResponse<TraceDto.BindResult> bind(@RequestBody BindRequest request) {
        return ApiResponse.ok(traceService.bind(request.drugId(), request.batchNo(), request.codes()));
    }

    @GetMapping
    public ApiResponse<PageResult<TraceDto>> page(
            @RequestParam(required = false) Long drugId,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) TraceStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(traceService.page(drugId, batchNo, status, keyword, page, size));
    }

    @GetMapping("/counts")
    public ApiResponse<Map<String, Long>> counts() {
        return ApiResponse.ok(traceService.counts());
    }

    @GetMapping("/code/{code}")
    public ApiResponse<TraceDto> byCode(@PathVariable String code) {
        return ApiResponse.ok(traceService.byCode(code));
    }

    @GetMapping("/prescription/{prescriptionId}")
    public ApiResponse<List<TraceDto>> byPrescription(@PathVariable Long prescriptionId) {
        return ApiResponse.ok(traceService.listByPrescription(prescriptionId));
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestBody IdsRequest request) {
        return ApiResponse.ok(traceService.upload(request.ids()));
    }

    @PostMapping("/return")
    public ApiResponse<Map<String, Object>> returnBack(@RequestBody IdsRequest request) {
        return ApiResponse.ok(traceService.returnBack(request.ids()));
    }

    public record BindRequest(Long drugId, String batchNo, List<String> codes) {
    }

    public record IdsRequest(List<Long> ids) {
    }
}
