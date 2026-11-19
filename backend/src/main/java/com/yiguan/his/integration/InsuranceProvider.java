package com.yiguan.his.integration;

import java.math.BigDecimal;

/**
 * 医保 / 第三方结算扩展点（SPI）。
 *
 * <p>不同地区医保规则差异极大，本系统只定义契约，各地区实现以 Spring Bean 形式接入：
 * 实现本接口并标注 {@code @Component}，前端即可在
 * {@code GET /api/insurance/providers} 中看到该实现。</p>
 */
public interface InsuranceProvider {

    /** 唯一标识，如 {@code shanghai}、{@code zhejiang}。 */
    String code();

    /** 展示名称。 */
    String name();

    /** 适用地区。 */
    String region();

    /** 是否已配置可用（未配置时前端置灰）。 */
    boolean enabled();

    /** 执行结算。 */
    SettlementResult settle(SettlementContext context);

    /** 结算上下文。 */
    record SettlementContext(Long orderId, String orderNo, String patientName, BigDecimal amount) {
    }

    /** 结算结果。 */
    record SettlementResult(String status, BigDecimal selfPayAmount, BigDecimal insuranceAmount, String message) {
    }
}
