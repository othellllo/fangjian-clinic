package com.fangjian.clinic.controller;

import com.fangjian.clinic.service.PrintService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 打印接口：返回带打印样式的 HTML，浏览器打开后自动调起打印。 */
@RestController
@RequestMapping("/api/print")
public class PrintController {

    private final PrintService printService;

    public PrintController(PrintService printService) {
        this.printService = printService;
    }

    @GetMapping(value = "/receipt/{chargeOrderId}", produces = MediaType.TEXT_HTML_VALUE)
    public String receipt(@PathVariable Long chargeOrderId) {
        return printService.receiptHtml(chargeOrderId);
    }

    @GetMapping(value = "/prescription/{prescriptionId}", produces = MediaType.TEXT_HTML_VALUE)
    public String prescription(@PathVariable Long prescriptionId) {
        return printService.prescriptionHtml(prescriptionId);
    }
}
