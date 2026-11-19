package com.fangjian.clinic.controller;

import com.fangjian.clinic.common.ApiResponse;
import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.service.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 库存流水与告警接口。 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/ledger")
    public ApiResponse<PageResult<StockService.LedgerItem>> ledger(
            @RequestParam(required = false) Long drugId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(stockService.ledger(drugId, page, size));
    }

    @GetMapping("/alerts")
    public ApiResponse<Map<String, Object>> alerts() {
        return ApiResponse.ok(stockService.alerts());
    }
}
