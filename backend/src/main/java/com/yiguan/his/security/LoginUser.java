package com.yiguan.his.security;

import com.yiguan.his.domain.enums.UserRole;

/**
 * 登录态用户，作为 Spring Security 的 principal 在请求线程内传递。
 */
public record LoginUser(Long id, String username, String realName, UserRole role) {
}
