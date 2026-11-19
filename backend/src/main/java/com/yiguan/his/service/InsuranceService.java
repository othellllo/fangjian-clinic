package com.yiguan.his.service;

import com.yiguan.his.common.BusinessException;
import com.yiguan.his.common.CodeGenerator;
import com.yiguan.his.domain.ChargeOrder;
import com.yiguan.his.domain.InsuranceSettlement;
import com.yiguan.his.domain.Patient;
import com.yiguan.his.integration.InsuranceProvider;
import com.yiguan.his.repository.ChargeOrderRepository;
import com.yiguan.his.repository.InsuranceSettlementRepository;
import com.yiguan.his.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 医保结算编排：选择地区实现 -> 调用 -> 落库。 */
@Service
public class InsuranceService {

    private final List<InsuranceProvider> providers;
    private final ChargeOrderRepository orderRepository;
    private final PatientRepository patientRepository;
    private final InsuranceSettlementRepository settlementRepository;

    public InsuranceService(List<InsuranceProvider> providers,
                            ChargeOrderRepository orderRepository,
                            PatientRepository patientRepository,
                            InsuranceSettlementRepository settlementRepository) {
        this.providers = providers;
        this.orderRepository = orderRepository;
        this.patientRepository = patientRepository;
        this.settlementRepository = settlementRepository;
    }

    public List<Map<String, Object>> providers() {
        return providers.stream().map(provider -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", provider.code());
            row.put("name", provider.name());
            row.put("region", provider.region());
            row.put("enabled", provider.enabled());
            return row;
        }).toList();
    }

    @Transactional
    public Map<String, Object> settle(Long chargeOrderId, String providerCode) {
        InsuranceProvider provider = providers.stream()
                .filter(item -> item.code().equals(providerCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未找到医保实现：" + providerCode));
        ChargeOrder order = orderRepository.findById(chargeOrderId)
                .orElseThrow(() -> BusinessException.notFound("收费单"));
        String patientName = patientRepository.findById(order.getPatientId())
                .map(Patient::getName).orElse(null);

        InsuranceProvider.SettlementResult result = provider.settle(
                new InsuranceProvider.SettlementContext(order.getId(), order.getOrderNo(),
                        patientName, order.getPayableAmount()));

        InsuranceSettlement settlement = new InsuranceSettlement();
        settlement.setSettlementNo(CodeGenerator.timeCode("I"));
        settlement.setOrderId(order.getId());
        settlement.setProviderCode(provider.code());
        settlement.setStatus(result.status());
        settlement.setSelfPayAmount(result.selfPayAmount());
        settlement.setInsuranceAmount(result.insuranceAmount());
        settlement.setMessage(result.message());
        settlementRepository.save(settlement);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("settlementNo", settlement.getSettlementNo());
        data.put("status", settlement.getStatus());
        data.put("selfPayAmount", settlement.getSelfPayAmount());
        data.put("insuranceAmount", settlement.getInsuranceAmount());
        data.put("message", settlement.getMessage());
        return data;
    }

    public List<Map<String, Object>> settlements(Long orderId) {
        return settlementRepository.findByOrderIdOrderByIdDesc(orderId).stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("settlementNo", item.getSettlementNo());
            row.put("providerCode", item.getProviderCode());
            row.put("status", item.getStatus());
            row.put("selfPayAmount", item.getSelfPayAmount());
            row.put("insuranceAmount", item.getInsuranceAmount());
            row.put("message", item.getMessage());
            row.put("createdAt", item.getCreatedAt());
            return row;
        }).toList();
    }
}
