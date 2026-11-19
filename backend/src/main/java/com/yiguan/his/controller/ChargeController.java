package com.yiguan.his.controller;

import com.yiguan.his.common.ApiResponse;
import com.yiguan.his.common.PageResult;
import com.yiguan.his.domain.enums.ChargeStatus;
import com.yiguan.his.domain.enums.ChargeType;
import com.yiguan.his.domain.enums.PayMethod;
import com.yiguan.his.dto.ChargeOrderDto;
import com.yiguan.his.service.ChargeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** 收费接口。 */
@RestController
@RequestMapping("/api/charges")
public class ChargeController {

    private final ChargeService chargeService;

    public ChargeController(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    @GetMapping
    public ApiResponse<PageResult<ChargeOrderDto>> page(
            @RequestParam(required = false) ChargeStatus status,
            @RequestParam(required = false) ChargeType type,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(chargeService.page(status, type, date, page, size));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return ApiResponse.ok(chargeService.summary(date));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChargeOrderDto> detail(@PathVariable Long id) {
        return ApiResponse.ok(chargeService.detail(id));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<ChargeOrderDto> pay(@PathVariable Long id, @RequestBody PayRequest request) {
        return ApiResponse.ok(chargeService.pay(id, request.method(), request.remark()));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<ChargeOrderDto> refund(@PathVariable Long id, @RequestBody RefundRequest request) {
        return ApiResponse.ok(chargeService.refund(id, request.reason()));
    }

    public record PayRequest(PayMethod method, String remark) {
    }

    public record RefundRequest(String reason) {
    }
}
