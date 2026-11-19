package com.fangjian.clinic.dto;

import com.fangjian.clinic.domain.enums.IssueLevel;

import java.math.BigDecimal;
import java.util.List;

/** 质控与处方审查结果。 */
public record QcResult(
        boolean passed,
        List<Issue> issues) {

    public record Issue(
            IssueLevel level,
            String type,
            String field,
            String message,
            String advice) {
    }

    /** 处方审查：issues + 自动标注的煎法。 */
    public record PrescriptionCheck(
            boolean passed,
            List<Issue> issues,
            List<Annotation> annotations) {

        public record Annotation(Long drugId, String drugName, String specialHandling, String note) {
        }
    }

    /** 术语联想结果。 */
    public record Suggestion(String term, String pinyin, String category, String source, Integer usageCount) {
    }

    /** AI 助写结果。 */
    public record AiResult(boolean enabled, String source, String text, String notice) {
    }

    /** 药品剂量核验入参。 */
    public record DoseUsage(Long drugId, BigDecimal dose, BigDecimal quantity) {
    }
}
