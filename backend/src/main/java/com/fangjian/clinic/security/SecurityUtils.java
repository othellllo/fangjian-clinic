package com.fangjian.clinic.security;

import com.fangjian.clinic.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 获取当前登录用户的工具方法。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return user;
    }

    public static Long currentUserId() {
        return currentUser().id();
    }

    public static boolean isAdmin() {
        return currentUser().role() == com.fangjian.clinic.domain.enums.UserRole.ADMIN;
    }
}
