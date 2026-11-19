package com.fangjian.clinic.dto;

import com.fangjian.clinic.domain.enums.ChargeStatus;
import com.fangjian.clinic.domain.enums.ChargeType;
import com.fangjian.clinic.domain.enums.PayMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 收费单视图。 */
public record ChargeOrderDto(
        Long id,
        String orderNo,
        Long visitId,
        Long patientId,
        String patientName,
        ChargeType type,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal payableAmount,
        BigDecimal paidAmount,
        ChargeStatus status,
        PayMethod payMethod,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        List<Item> items) {

    public record Item(
            Long id,
            ChargeType itemType,
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount) {
    }
}
