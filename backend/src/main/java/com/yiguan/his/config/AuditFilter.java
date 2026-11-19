package com.yiguan.his.config;

import com.yiguan.his.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * 操作审计过滤器：自动记录所有「写操作」（非 GET）请求。
 *
 * <p>放在过滤器链最内层（LOWEST_PRECEDENCE），此时 SecurityContext 仍有效，
 * 可以拿到操作人；不依赖在业务代码里到处埋点，新增接口自动被审计。</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditFilter extends OncePerRequestFilter {

    private static final Map<String, String> MODULE_NAMES = Map.ofEntries(
            Map.entry("patients", "患者档案"),
            Map.entry("visits", "挂号就诊"),
            Map.entry("prescriptions", "处方"),
            Map.entry("charges", "收费"),
            Map.entry("drugs", "药品"),
            Map.entry("stock", "库存"),
            Map.entry("decoctions", "代煎"),
            Map.entry("appointments", "预约"),
            Map.entry("members", "会员"),
            Map.entry("users", "员工"),
            Map.entry("config", "医馆配置"),
            Map.entry("insurance", "医保"),
            Map.entry("patient", "患者端"));

    private final AuditService auditService;

    public AuditFilter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        // 只审计写操作；登录接口由 AuthService 单独记录
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        if (uri.startsWith("/api/auth/login") || uri.startsWith("/api/patient/auth/login")) {
            return true;
        }
        return !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        boolean success = true;
        String error = null;
        try {
            filterChain.doFilter(request, response);
            success = response.getStatus() < 400;
        } catch (Exception ex) {
            success = false;
            error = ex.getMessage();
            throw ex;
        } finally {
            try {
                String uri = request.getRequestURI();
                String[] segments = uri.substring("/api/".length()).split("/");
                String moduleKey = segments.length > 0 ? segments[0] : "other";
                String module = MODULE_NAMES.getOrDefault(moduleKey, moduleKey);
                Long targetId = null;
                for (int i = segments.length - 1; i >= 0; i--) {
                    try {
                        targetId = Long.parseLong(segments[i]);
                        break;
                    } catch (NumberFormatException ignored) {
                        // 继续向前找数字段
                    }
                }
                String action = request.getMethod() + " " + uri;
                String detail = success
                        ? "耗时 " + (System.currentTimeMillis() - start) + "ms"
                        : "失败：" + (error == null ? "HTTP " + response.getStatus() : error);
                auditService.record(module, action, moduleKey, targetId,
                        success ? "SUCCESS" : "FAIL", detail);
            } catch (Exception ignored) {
                // 审计失败不影响主流程
            }
        }
    }
}
