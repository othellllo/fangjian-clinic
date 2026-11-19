package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.dto.MemberDto;
import com.fangjian.clinic.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录相关接口。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthService.LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }

    @GetMapping("/me")
    public ApiResponse<MemberDto.User> me() {
        return ApiResponse.ok(authService.currentUser());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // 无状态 JWT，前端丢弃 token 即可
        return ApiResponse.ok();
    }

    public record LoginRequest(@NotBlank(message = "用户名不能为空") String username,
                               @NotBlank(message = "密码不能为空") String password) {
    }
}
