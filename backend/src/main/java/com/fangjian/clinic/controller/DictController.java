package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.common.DosageDict;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础字典：给药途径与用药频次。
 *
 * <p>开方时医生可在下拉里选「口服 / 一日三次（tid）」，系统拼成规范用法文本；
 * 也允许自定义（前端 el-select 的 allow-create）。</p>
 */
@RestController
@RequestMapping("/api/dict")
public class DictController {

    @GetMapping("/dosage")
    public ApiResponse<Map<String, Object>> dosage() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("routes", DosageDict.ROUTES.entrySet().stream()
                .map(entry -> row(entry.getKey(), entry.getValue())).toList());
        data.put("frequencies", DosageDict.FREQUENCIES.entrySet().stream()
                .map(entry -> row(entry.getKey(), entry.getValue())).toList());
        return ApiResponse.ok(data);
    }

    private static Map<String, String> row(String code, String label) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("label", label);
        return row;
    }
}
