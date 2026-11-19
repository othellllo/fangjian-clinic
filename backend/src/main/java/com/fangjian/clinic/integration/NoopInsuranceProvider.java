package com.fangjian.clinic.integration;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 默认占位实现：未接入任何地区医保时使用，直接返回“未开通”。
 * 真实接入时新增一个 {@link InsuranceProvider} 实现即可，无需改动业务代码。
 */
@Component
public class NoopInsuranceProvider implements InsuranceProvider {

    @Override
    public String code() {
        return "noop";
    }

    @Override
    public String name() {
        return "医保接口（未开通）";
    }

    @Override
    public String region() {
        return "全国";
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public SettlementResult settle(SettlementContext context) {
        return new SettlementResult("UNSUPPORTED", context.amount(), BigDecimal.ZERO,
                "当前未接入医保结算接口，请使用自费支付方式");
    }
}
