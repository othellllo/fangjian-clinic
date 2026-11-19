package com.fangjian.clinic.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.domain.Drug;
import com.fangjian.clinic.domain.enums.DrugCategory;
import com.fangjian.clinic.repository.DrugRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理设置：打印参数、收款方式、药品库存上下限。
 * 轻量配置存 sys_config（键值对），避免为几个开关建表。
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private static final String KEY_PRINT_WIDTH = "print.receipt_width";
    private static final String KEY_PRINT_HEADER = "print.header";
    private static final String KEY_PRINT_FOOTER = "print.footer";
    private static final String KEY_PRINT_COPIES = "print.copies";
    private static final String KEY_PRINT_SHOW_CLINIC = "print.show_clinic_name";
    private static final String KEY_PAYMENT_METHODS = "payment.methods";

    /** 默认收款方式（顺序即展示顺序）。 */
    private static final List<String> DEFAULT_METHODS = List.of(
            "CASH", "WECHAT", "ALIPAY", "BANK_CARD", "MEMBER_BALANCE", "INSURANCE");

    private static final Map<String, String> METHOD_NAMES = Map.of(
            "CASH", "现金", "WECHAT", "微信", "ALIPAY", "支付宝",
            "BANK_CARD", "银行卡", "MEMBER_BALANCE", "会员余额", "INSURANCE", "医保");

    private final ConfigService configService;
    private final DrugRepository drugRepository;
    private final ObjectMapper objectMapper;

    public SettingsService(ConfigService configService, DrugRepository drugRepository,
                           ObjectMapper objectMapper) {
        this.configService = configService;
        this.drugRepository = drugRepository;
        this.objectMapper = objectMapper;
    }

    /* ---------------- 打印设置 ---------------- */

    public Map<String, Object> printSettings() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("receiptWidth", configService.get(KEY_PRINT_WIDTH, "58"));
        data.put("header", configService.get(KEY_PRINT_HEADER, ""));
        data.put("footer", configService.get(KEY_PRINT_FOOTER, "谢谢惠顾，祝您健康"));
        try {
            data.put("copies", Integer.parseInt(configService.get(KEY_PRINT_COPIES, "1")));
        } catch (NumberFormatException ex) {
            data.put("copies", 1);
        }
        data.put("showClinicName", !"false".equalsIgnoreCase(configService.get(KEY_PRINT_SHOW_CLINIC, "true")));
        data.put("clinicName", configService.clinicInfo().get("name"));
        return data;
    }

    @Transactional
    public Map<String, Object> updatePrintSettings(Map<String, Object> body) {
        if (body.containsKey("receiptWidth")) {
            String width = String.valueOf(body.get("receiptWidth"));
            configService.set(KEY_PRINT_WIDTH, "80".equals(width) ? "80" : "58", "小票纸宽");
        }
        if (body.containsKey("header")) {
            configService.set(KEY_PRINT_HEADER, str(body.get("header")), "小票抬头");
        }
        if (body.containsKey("footer")) {
            configService.set(KEY_PRINT_FOOTER, str(body.get("footer")), "小票页脚");
        }
        if (body.containsKey("copies")) {
            int copies = Math.max(1, Math.min(3, toInt(body.get("copies"), 1)));
            configService.set(KEY_PRINT_COPIES, String.valueOf(copies), "打印份数");
        }
        if (body.containsKey("showClinicName")) {
            configService.set(KEY_PRINT_SHOW_CLINIC, String.valueOf(Boolean.TRUE.equals(body.get("showClinicName"))),
                    "小票是否显示机构名");
        }
        return printSettings();
    }

    /* ---------------- 收款方式 ---------------- */

    public List<Map<String, Object>> paymentMethods() {
        String json = configService.get(KEY_PAYMENT_METHODS, null);
        if (json == null || json.isBlank()) {
            return defaultMethods();
        }
        try {
            List<Map<String, Object>> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (parsed == null || parsed.isEmpty()) {
                return defaultMethods();
            }
            return parsed.stream().sorted(Comparator.comparingInt(row -> toInt(row.get("sort"), 0))).toList();
        } catch (Exception ex) {
            log.warn("收款方式配置解析失败，使用默认值：{}", ex.getMessage());
            return defaultMethods();
        }
    }

    private List<Map<String, Object>> defaultMethods() {
        List<Map<String, Object>> list = new ArrayList<>();
        int sort = 1;
        for (String method : DEFAULT_METHODS) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("method", method);
            row.put("name", METHOD_NAMES.getOrDefault(method, method));
            row.put("enabled", true);
            row.put("sort", sort++);
            list.add(row);
        }
        return list;
    }

    @Transactional
    public List<Map<String, Object>> updatePaymentMethods(List<Map<String, Object>> methods) {
        if (methods == null || methods.isEmpty()) {
            throw new BusinessException("收款方式不能为空");
        }
        boolean anyEnabled = methods.stream().anyMatch(row -> Boolean.TRUE.equals(row.get("enabled")));
        if (!anyEnabled) {
            throw new BusinessException("至少需要启用一种收款方式");
        }
        try {
            configService.set(KEY_PAYMENT_METHODS, objectMapper.writeValueAsString(methods), "收款方式配置");
        } catch (Exception ex) {
            throw new BusinessException("收款方式保存失败：" + ex.getMessage());
        }
        return paymentMethods();
    }

    /* ---------------- 药品库存上下限 ---------------- */

    @Transactional
    public Map<String, Object> batchDrugLimits(DrugCategory category, BigDecimal stockWarn, BigDecimal stockMax) {
        if (category == null) {
            throw new BusinessException("请选择药品分类");
        }
        List<Drug> drugs = drugRepository.findByCategoryAndStatus(category, 1);
        BigDecimal warn = stockWarn == null ? BigDecimal.ZERO : stockWarn;
        BigDecimal max = stockMax == null ? BigDecimal.ZERO : stockMax;
        for (Drug drug : drugs) {
            drug.setStockWarn(warn);
            drug.setStockMax(max);
        }
        drugRepository.saveAll(drugs);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("category", category.name());
        data.put("stockWarn", warn);
        data.put("stockMax", max);
        data.put("updated", drugs.size());
        return data;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
