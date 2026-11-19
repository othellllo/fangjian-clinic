package com.fangjian.clinic.service;

import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.domain.AuditLog;
import com.fangjian.clinic.dto.AuditLogDto;
import com.fangjian.clinic.repository.AuditLogRepository;
import com.fangjian.clinic.security.LoginUser;
import com.fangjian.clinic.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 审计日志写入与查询。写日志失败不能影响业务，因此全部捕获。 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** 记录当前登录用户的操作。 */
    public void record(String module, String action, String targetType, Long targetId,
                       String result, String detail) {
        try {
            LoginUser user = null;
            try {
                user = SecurityUtils.currentUser();
            } catch (Exception ignored) {
                // 匿名场景（如登录失败）
            }
            AuditLog entity = new AuditLog();
            if (user != null) {
                entity.setUserId(user.id());
                entity.setUsername(user.username());
                entity.setRealName(user.realName());
                entity.setRole(user.role().name());
            }
            entity.setModule(module);
            entity.setAction(action);
            entity.setTargetType(targetType);
            entity.setTargetId(targetId);
            entity.setResult(result == null ? "SUCCESS" : result);
            entity.setDetail(truncate(detail, 1900));
            entity.setIp(clientIp());
            repository.save(entity);
        } catch (Exception ex) {
            log.warn("写审计日志失败：{}", ex.getMessage());
        }
    }

    /** 匿名场景（登录尝试）使用。 */
    public void recordAnonymous(String username, String module, String action, String result, String detail) {
        try {
            AuditLog entity = new AuditLog();
            entity.setUsername(truncate(username, 50));
            entity.setModule(module);
            entity.setAction(action);
            entity.setResult(result);
            entity.setDetail(truncate(detail, 1900));
            entity.setIp(clientIp());
            repository.save(entity);
        } catch (Exception ex) {
            log.warn("写审计日志失败：{}", ex.getMessage());
        }
    }

    public PageResult<AuditLogDto> page(String module, String keyword, LocalDate start, LocalDate end,
                                        int page, int size) {
        LocalDateTime from = start == null ? null : start.atStartOfDay();
        LocalDateTime to = end == null ? null : end.plusDays(1).atStartOfDay();
        // 空字符串表示"全部"，统一归一化为 null，避免精确匹配到空值
        String moduleFilter = (module == null || module.isBlank()) ? null : module;
        String keywordFilter = keyword == null ? "" : keyword.trim();
        Page<AuditLog> result = repository.search(moduleFilter, keywordFilter, from, to,
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, AuditService::toDto);
    }

    public List<String> modules() {
        return repository.findModules();
    }

    static AuditLogDto toDto(AuditLog entity) {
        return new AuditLogDto(entity.getId(), entity.getUsername(), entity.getRealName(), entity.getRole(),
                entity.getModule(), entity.getAction(), entity.getTargetType(), entity.getTargetId(),
                entity.getDetail(), entity.getIp(), entity.getResult(), entity.getCreatedAt());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String clientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return truncate(forwarded.split(",")[0].trim(), 64);
            }
            return truncate(request.getRemoteAddr(), 64);
        } catch (Exception ex) {
            return null;
        }
    }
}
