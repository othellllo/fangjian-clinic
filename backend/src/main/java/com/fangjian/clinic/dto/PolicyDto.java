package com.fangjian.clinic.dto;

import com.fangjian.clinic.domain.enums.PolicyCategory;
import com.fangjian.clinic.domain.enums.PolicyStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 医保政策视图。 */
public record PolicyDto(
        Long id,
        String title,
        String issuer,
        String docNo,
        PolicyCategory category,
        LocalDate publishDate,
        LocalDate effectiveDate,
        LocalDate expireDate,
        String summary,
        String content,
        String sourceUrl,
        PolicyStatus status,
        LocalDateTime createdAt) {
}
