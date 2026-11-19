package com.yiguan.his.controller;

import com.yiguan.his.common.ApiResponse;
import com.yiguan.his.common.PageResult;
import com.yiguan.his.dto.MemberDto;
import com.yiguan.his.service.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/** 会员卡与储值接口。 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<PageResult<MemberDto>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(memberService.page(keyword, page, size));
    }

    @PostMapping
    public ApiResponse<MemberDto> create(@RequestBody CreateMemberRequest request) {
        return ApiResponse.ok(memberService.create(request.patientId(), request.name(), request.phone(), request.level()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(memberService.detail(id));
    }

    @PostMapping("/{id}/recharge")
    public ApiResponse<MemberDto> recharge(@PathVariable Long id, @RequestBody RechargeRequest request) {
        return ApiResponse.ok(memberService.recharge(id, request.amount(), request.method(), request.remark()));
    }

    @GetMapping("/{id}/txns")
    public ApiResponse<PageResult<MemberDto.Txn>> txns(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(memberService.txns(id, page, size));
    }

    public record CreateMemberRequest(Long patientId, String name, String phone, Integer level) {
    }

    public record RechargeRequest(BigDecimal amount, String method, String remark) {
    }
}
