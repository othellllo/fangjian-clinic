package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.domain.SysConfig;
import com.fangjian.clinic.repository.SysConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** 医馆基础配置（键值对存储）。 */
@Service
public class ConfigService {

    public static final String KEY_CLINIC_NAME = "clinic.name";
    public static final String KEY_CLINIC_ADDRESS = "clinic.address";
    public static final String KEY_CLINIC_PHONE = "clinic.phone";
    public static final String KEY_CLINIC_NOTICE = "clinic.notice";
    public static final String KEY_REGISTER_FEE = "clinic.register_fee";

    private final SysConfigRepository repository;

    public ConfigService(SysConfigRepository repository) {
        this.repository = repository;
    }

    public String get(String key, String defaultValue) {
        return repository.findByConfigKey(key)
                .map(SysConfig::getConfigValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(defaultValue);
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        String value = get(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @Transactional
    public void set(String key, String value, String remark) {
        SysConfig config = repository.findByConfigKey(key).orElseGet(() -> {
            SysConfig created = new SysConfig();
            created.setConfigKey(key);
            return created;
        });
        config.setConfigValue(value);
        if (remark != null) {
            config.setRemark(remark);
        }
        repository.save(config);
    }

    public Map<String, String> clinicInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("name", get(KEY_CLINIC_NAME, "示范中医馆"));
        info.put("address", get(KEY_CLINIC_ADDRESS, ""));
        info.put("phone", get(KEY_CLINIC_PHONE, ""));
        info.put("notice", get(KEY_CLINIC_NOTICE, ""));
        return info;
    }

    @Transactional
    public void updateClinicInfo(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException("没有需要保存的内容");
        }
        saveIfPresent(KEY_CLINIC_NAME, values.get("name"), "医馆名称");
        saveIfPresent(KEY_CLINIC_ADDRESS, values.get("address"), "医馆地址");
        saveIfPresent(KEY_CLINIC_PHONE, values.get("phone"), "联系电话");
        saveIfPresent(KEY_CLINIC_NOTICE, values.get("notice"), "公告");
    }

    private void saveIfPresent(String key, String value, String remark) {
        if (value != null) {
            set(key, value, remark);
        }
    }
}
