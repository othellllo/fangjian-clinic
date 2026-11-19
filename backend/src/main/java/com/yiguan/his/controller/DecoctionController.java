package com.yiguan.his.controller;

import com.yiguan.his.common.ApiResponse;
import com.yiguan.his.common.PageResult;
import com.yiguan.his.domain.enums.DecoctionStatus;
import com.yiguan.his.dto.DecoctionDto;
import com.yiguan.his.service.DecoctionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 代煎管理接口。 */
@RestController
@RequestMapping("/api/decoctions")
public class DecoctionController {

    private final DecoctionService decoctionService;

    public DecoctionController(DecoctionService decoctionService) {
        this.decoctionService = decoctionService;
    }

    @GetMapping
    public ApiResponse<PageResult<DecoctionDto>> page(
            @RequestParam(required = false) DecoctionStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(decoctionService.page(status, keyword, page, size));
    }

    @GetMapping("/counts")
    public ApiResponse<Map<String, Long>> counts() {
        return ApiResponse.ok(decoctionService.counts());
    }

    @GetMapping("/{id}")
    public ApiResponse<DecoctionDto> detail(@PathVariable Long id) {
        return ApiResponse.ok(decoctionService.detail(id));
    }

    @PostMapping("/{id}/cook")
    public ApiResponse<DecoctionDto> cook(@PathVariable Long id) {
        return ApiResponse.ok(decoctionService.cook(id));
    }

    @PostMapping("/{id}/ready")
    public ApiResponse<DecoctionDto> ready(@PathVariable Long id) {
        return ApiResponse.ok(decoctionService.ready(id));
    }

    @PostMapping("/{id}/deliver")
    public ApiResponse<DecoctionDto> deliver(@PathVariable Long id, @RequestBody(required = false) DeliverRequest request) {
        String deliverType = request == null ? "SELF" : request.deliverType();
        String remark = request == null ? null : request.remark();
        return ApiResponse.ok(decoctionService.deliver(id, deliverType, remark));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<DecoctionDto> cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
        return ApiResponse.ok(decoctionService.cancel(id, request == null ? null : request.reason()));
    }

    public record DeliverRequest(String deliverType, String remark) {
    }

    public record CancelRequest(String reason) {
    }
}
