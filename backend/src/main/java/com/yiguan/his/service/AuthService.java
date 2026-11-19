package com.yiguan.his.service;

import com.yiguan.his.common.BusinessException;
import com.yiguan.his.domain.SysUser;
import com.yiguan.his.dto.MemberDto;
import com.yiguan.his.repository.SysUserRepository;
import com.yiguan.his.security.JwtService;
import com.yiguan.his.security.LoginUser;
import com.yiguan.his.security.SecurityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 登录认证。登录成功与失败都会写审计日志。 */
@Service
public class AuthService {

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(SysUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    public LoginResult login(String username, String password) {
        try {
            SysUser user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
            if (user.getStatus() == null || user.getStatus() != 1) {
                throw new BusinessException(403, "账号已停用，请联系管理员");
            }
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new BusinessException(401, "用户名或密码错误");
            }
            LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRealName(), user.getRole());
            auditService.recordAnonymous(user.getUsername(), "认证", "员工登录成功", "SUCCESS",
                    "角色 " + user.getRole().name());
            return new LoginResult(jwtService.generate(loginUser), UserService.toDto(user));
        } catch (BusinessException ex) {
            auditService.recordAnonymous(username, "认证", "员工登录失败", "FAIL", ex.getMessage());
            throw ex;
        }
    }

    public MemberDto.User currentUser() {
        LoginUser loginUser = SecurityUtils.currentUser();
        SysUser user = userRepository.findById(loginUser.id())
                .orElseThrow(() -> new BusinessException(401, "登录用户不存在"));
        return UserService.toDto(user);
    }

    public record LoginResult(String token, MemberDto.User user) {
    }
}
