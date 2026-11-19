package com.yiguan.his.dto;

import com.yiguan.his.domain.enums.PolicyCategory;
import com.yiguan.his.domain.enums.PolicyStatus;

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
