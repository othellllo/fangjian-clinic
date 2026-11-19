package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.domain.enums.DrugCategory;
import com.fangjian.clinic.domain.enums.PregnancyLevel;
import com.fangjian.clinic.domain.enums.SpecialHandling;
import com.fangjian.clinic.domain.enums.ToxicityLevel;
import com.fangjian.clinic.dto.DrugDto;
import com.fangjian.clinic.service.DrugService;
import com.fangjian.clinic.service.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** 药品字典、临床属性与入库接口。 */
@RestController
@RequestMapping("/api/drugs")
public class DrugController {

    private final DrugService drugService;
    private final StockService stockService;

    public DrugController(DrugService drugService, StockService stockService) {
        this.drugService = drugService;
        this.stockService = stockService;
    }

    @GetMapping
    public ApiResponse<PageResult<DrugDto>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DrugCategory category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(drugService.page(keyword, category, page, size));
    }

    @PostMapping
    public ApiResponse<DrugDto> create(@RequestBody DrugRequest request) {
        return ApiResponse.ok(drugService.create(request.code(), request.name(), request.pinyin(),
                request.category(), request.spec(), request.unit(), request.price(),
                request.costPrice(), request.stockWarn()));
    }

    @PutMapping("/{id}")
    public ApiResponse<DrugDto> update(@PathVariable Long id, @RequestBody DrugRequest request) {
        return ApiResponse.ok(drugService.update(id, request.name(), request.pinyin(), request.category(),
                request.spec(), request.unit(), request.price(), request.costPrice(),
                request.stockWarn(), request.status()));
    }

    /** 临床属性：特殊煎法、剂量范围、妊娠风险、毒性、功效。 */
    @PutMapping("/{id}/clinical")
    public ApiResponse<DrugDto> updateClinical(@PathVariable Long id, @RequestBody ClinicalRequest request) {
        return ApiResponse.ok(drugService.updateClinical(id, request.specialHandling(), request.doseMin(),
                request.doseMax(), request.pregnancyLevel(), request.toxicityLevel(), request.functions()));
    }

    @GetMapping("/{id}/stock")
    public ApiResponse<Map<String, Object>> stock(@PathVariable Long id) {
        return ApiResponse.ok(stockService.stockDetail(id));
    }

    @PostMapping("/{id}/stock-in")
    public ApiResponse<Map<String, Object>> stockIn(@PathVariable Long id, @RequestBody StockInRequest request) {
        return ApiResponse.ok(stockService.stockIn(id, request.batchNo(), request.productionDate(),
                request.expiryDate(), request.quantity(), request.costPrice(), request.remark()));
    }

    @PostMapping("/{id}/stock-adjust")
    public ApiResponse<Map<String, Object>> stockAdjust(@PathVariable Long id, @RequestBody AdjustRequest request) {
        return ApiResponse.ok(stockService.adjust(id, request.batchNo(), request.quantity(), request.remark()));
    }

    public record DrugRequest(String code, String name, String pinyin, DrugCategory category, String spec,
                              String unit, BigDecimal price, BigDecimal costPrice, BigDecimal stockWarn,
                              Integer status) {
    }

    public record ClinicalRequest(SpecialHandling specialHandling, BigDecimal doseMin, BigDecimal doseMax,
                                  PregnancyLevel pregnancyLevel, ToxicityLevel toxicityLevel, String functions) {
    }

    public record StockInRequest(String batchNo, LocalDate productionDate, LocalDate expiryDate,
                                 BigDecimal quantity, BigDecimal costPrice, String remark) {
    }

    public record AdjustRequest(String batchNo, BigDecimal quantity, String remark) {
    }
}
