package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.domain.enums.ImportType;
import com.fangjian.clinic.dto.ImportDtos;
import com.fangjian.clinic.service.ImportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Excel 数据迁移接口（仅管理员）。 */
@RestController
@RequestMapping("/api/import")
@PreAuthorize("hasRole('ADMIN')")
public class ImportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/templates/{type}")
    public ResponseEntity<byte[]> template(@PathVariable ImportType type) {
        byte[] content = importService.template(type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"import-template-" + type.name() + ".xlsx\"")
                .contentType(XLSX)
                .body(content);
    }

    @PostMapping("/preview")
    public ApiResponse<ImportDtos.PreviewResult> preview(@RequestParam("file") MultipartFile file,
                                                         @RequestParam("type") ImportType type) {
        return ApiResponse.ok(importService.preview(type, file));
    }

    @PostMapping("/execute")
    public ApiResponse<ImportDtos.ExecuteResult> execute(@RequestParam("file") MultipartFile file,
                                                         @RequestParam("type") ImportType type,
                                                         @RequestParam(value = "mode", required = false) String mode) {
        return ApiResponse.ok(importService.execute(type, file, mode));
    }
}
