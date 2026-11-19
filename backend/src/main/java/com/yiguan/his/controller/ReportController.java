package com.yiguan.his.controller;

import com.yiguan.his.common.ApiResponse;
import com.yiguan.his.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 经营报表接口。 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/daily")
    public ApiResponse<Map<String, Object>> daily(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return ApiResponse.ok(reportService.daily(date));
    }

    @GetMapping("/range")
    public ApiResponse<List<Map<String, Object>>> range(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return ApiResponse.ok(reportService.range(start, end));
    }

    @GetMapping("/doctor-performance")
    public ApiResponse<List<Map<String, Object>>> doctorPerformance(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return ApiResponse.ok(reportService.doctorPerformance(start, end));
    }

    @GetMapping("/drug-sales")
    public ApiResponse<List<Map<String, Object>>> drugSales(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return ApiResponse.ok(reportService.drugSales(start, end));
    }

    @GetMapping("/insurance")
    public ApiResponse<List<Map<String, Object>>> insurance(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return ApiResponse.ok(reportService.insuranceStats(start, end));
    }

    @GetMapping("/insurance/daily")
    public ApiResponse<Map<String, Object>> insuranceDaily(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return ApiResponse.ok(reportService.insuranceDaily(date));
    }
}
