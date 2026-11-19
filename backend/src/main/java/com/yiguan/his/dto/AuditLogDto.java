package com.yiguan.his.dto;

import java.time.LocalDateTime;

/** 审计日志视图。 */
public record AuditLogDto(
        Long id,
        String username,
        String realName,
        String role,
        String module,
        String action,
        String targetType,
        Long targetId,
        String detail,
        String ip,
        String result,
        LocalDateTime createdAt) {
}
