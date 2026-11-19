package com.yiguan.his.controller;

import com.yiguan.his.service.ExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 数据导出接口（CSV，UTF-8 BOM，Excel 可直接打开）。 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/{type}.csv")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<byte[]> export(
            @PathVariable String type,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        if ("audit".equalsIgnoreCase(type)) {
            // 审计数据仅管理员可导出
            if (!com.yiguan.his.security.SecurityUtils.isAdmin()) {
                return ResponseEntity.status(403).build();
            }
        }
        byte[] content = exportService.export(type, start, end);
        String filename = "yiguan-" + type + "-" + LocalDateTime.now().format(STAMP) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(content);
    }
}
