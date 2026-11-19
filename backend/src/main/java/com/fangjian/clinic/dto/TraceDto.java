package com.fangjian.clinic.dto;

import com.fangjian.clinic.domain.enums.TraceStatus;

import java.time.LocalDateTime;

/** 追溯码视图。 */
public record TraceDto(
        Long id,
        Long drugId,
        String drugName,
        String batchNo,
        String code,
        TraceStatus status,
        LocalDateTime bindTime,
        Long prescriptionId,
        String patientName,
        LocalDateTime dispenseTime,
        String operatorName,
        String uploadStatus) {

    /** 入库绑定结果明细。 */
    public record BindResult(int success, int failed, java.util.List<String> errors) {
    }

    /** 发药时每个药品的扫码结果。 */
    public record DispenseResult(Long drugId, String drugName, int required, int provided) {
    }
}
