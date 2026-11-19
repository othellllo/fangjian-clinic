package com.yiguan.his.controller;

import com.yiguan.his.common.ApiResponse;
import com.yiguan.his.common.PageResult;
import com.yiguan.his.domain.enums.UserRole;
import com.yiguan.his.dto.MemberDto;
import com.yiguan.his.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 员工账号接口。 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/doctors")
    public ApiResponse<List<MemberDto.User>> doctors() {
        return ApiResponse.ok(userService.doctors());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<MemberDto.User>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(userService.page(page, size));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MemberDto.User> create(@RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userService.create(request.username(), request.password(), request.realName(),
                request.role(), request.title(), request.dept(), request.phone()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MemberDto.User> update(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userService.update(id, request.realName(), request.role(), request.title(),
                request.dept(), request.phone(), request.password()));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        userService.updateStatus(id, request.status());
        return ApiResponse.ok();
    }

    public record CreateUserRequest(String username, String password, String realName, UserRole role,
                                    String title, String dept, String phone) {
    }

    public record UpdateUserRequest(String realName, UserRole role, String title, String dept,
                                    String phone, String password) {
    }

    public record StatusRequest(Integer status) {
    }
}
