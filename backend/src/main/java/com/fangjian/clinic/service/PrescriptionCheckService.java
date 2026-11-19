package com.fangjian.clinic.service;

import com.fangjian.clinic.domain.Drug;
import com.fangjian.clinic.domain.DrugInteractionRule;
import com.fangjian.clinic.domain.enums.InteractionRuleType;
import com.fangjian.clinic.domain.enums.IssueLevel;
import com.fangjian.clinic.domain.enums.SpecialHandling;
import com.fangjian.clinic.domain.enums.ToxicityLevel;
import com.fangjian.clinic.dto.QcResult;
import com.fangjian.clinic.repository.DrugInteractionRuleRepository;
import com.fangjian.clinic.repository.DrugRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 处方安全审查：十八反、十九畏、妊娠禁忌、毒性药、剂量范围、特殊煎法标注。
 *
 * <p>规则来源见 docs/clinical-design.md 与 V5 迁移脚本；妊娠状态未知时只提示不阻断。</p>
 */
@Service
public class PrescriptionCheckService {

    private final DrugRepository drugRepository;
    private final DrugInteractionRuleRepository ruleRepository;

    public PrescriptionCheckService(DrugRepository drugRepository,
                                    DrugInteractionRuleRepository ruleRepository) {
        this.drugRepository = drugRepository;
        this.ruleRepository = ruleRepository;
    }

    /**
     * @param usages  处方明细（drugId + 单次剂量 + 数量）
     * @param pregnant TRUE 妊娠 / FALSE 非妊娠 / null 未知
     */
    public QcResult.PrescriptionCheck check(List<QcResult.DoseUsage> usages, Boolean pregnant) {
        List<QcResult.Issue> issues = new ArrayList<>();
        List<QcResult.PrescriptionCheck.Annotation> annotations = new ArrayList<>();
        if (usages == null || usages.isEmpty()) {
            return new QcResult.PrescriptionCheck(true, issues, annotations);
        }

        Map<Long, Drug> drugs = new HashMap<>();
        Set<String> names = new LinkedHashSet<>();
        for (QcResult.DoseUsage usage : usages) {
            Drug drug = drugRepository.findById(usage.drugId()).orElse(null);
            if (drug == null) {
                continue;
            }
            drugs.put(drug.getId(), drug);
            names.add(drug.getName());
        }

        // 1. 十八反 / 十九畏
        for (DrugInteractionRule rule : ruleRepository.findByRuleTypeIn(List.of(
                InteractionRuleType.EIGHTEEN_ANTAGONISM, InteractionRuleType.NINETEEN_INCOMPATIBILITY))) {
            if (rule.getItemB() == null) {
                continue;
            }
            if (matches(names, rule.getItemA()) && matches(names, rule.getItemB())) {
                issues.add(new QcResult.Issue(rule.getSeverity(), rule.getRuleType().name(), "items",
                        rule.getItemA() + " 与 " + rule.getItemB() + " 属配伍禁忌",
                        rule.getAdvice()));
            }
        }

        // 2. 妊娠禁忌
        if (pregnant == null) {
            issues.add(new QcResult.Issue(IssueLevel.INFO, "PREGNANCY_UNKNOWN", "patient",
                    "妊娠状态未知，未执行妊娠禁忌审查", "女性育龄患者建议登记妊娠状态"));
        } else if (pregnant) {
            for (DrugInteractionRule rule : ruleRepository.findByRuleTypeIn(List.of(
                    InteractionRuleType.PREGNANCY_FORBIDDEN, InteractionRuleType.PREGNANCY_CAUTION))) {
                if (matches(names, rule.getItemA())) {
                    issues.add(new QcResult.Issue(rule.getSeverity(), rule.getRuleType().name(), "items",
                            "含妊娠" + (rule.getRuleType() == InteractionRuleType.PREGNANCY_FORBIDDEN ? "禁用" : "慎用")
                                    + "药：" + rule.getItemA(),
                            rule.getAdvice()));
                }
            }
        }

        // 2.5 药品自身妊娠标记兜底（规则表之外的品种）
        if (Boolean.TRUE.equals(pregnant)) {
            for (Drug drug : drugs.values()) {
                if (drug.getPregnancyLevel() == com.fangjian.clinic.domain.enums.PregnancyLevel.FORBIDDEN) {
                    issues.add(new QcResult.Issue(IssueLevel.BLOCK, "PREGNANCY_FORBIDDEN", "items",
                            "妊娠禁用：" + drug.getName(), "该药妊娠期禁用，请调整处方"));
                } else if (drug.getPregnancyLevel() == com.fangjian.clinic.domain.enums.PregnancyLevel.CAUTION) {
                    issues.add(new QcResult.Issue(IssueLevel.WARN, "PREGNANCY_CAUTION", "items",
                            "妊娠慎用：" + drug.getName(), "如确需使用请权衡利弊并记录"));
                }
            }
        }

        // 3. 毒性药与剂量
        for (QcResult.DoseUsage usage : usages) {
            Drug drug = drugs.get(usage.drugId());
            if (drug == null) {
                continue;
            }
            if (drug.getToxicityLevel() == ToxicityLevel.TOXIC || drug.getToxicityLevel() == ToxicityLevel.MILD) {
                issues.add(new QcResult.Issue(IssueLevel.WARN, "TOXIC_DRUG", "items",
                        "含毒性中药：" + drug.getName(),
                        "毒性药需严格控制剂量与煎法，建议双签字核对"));
            }
            BigDecimal dose = usage.dose();
            if (dose != null && drug.getDoseMin() != null && drug.getDoseMax() != null) {
                if (dose.compareTo(drug.getDoseMin()) < 0 || dose.compareTo(drug.getDoseMax()) > 0) {
                    issues.add(new QcResult.Issue(IssueLevel.WARN, "DOSE_RANGE", "items",
                            drug.getName() + " 单次剂量 " + dose.stripTrailingZeros().toPlainString()
                                    + "g 超出常用量 "
                                    + drug.getDoseMin().stripTrailingZeros().toPlainString() + "-"
                                    + drug.getDoseMax().stripTrailingZeros().toPlainString() + "g",
                            "请核对剂量，超量使用需医师确认并记录理由"));
                }
            }
            if (drug.getSpecialHandling() != null && drug.getSpecialHandling() != SpecialHandling.NORMAL) {
                annotations.add(new QcResult.PrescriptionCheck.Annotation(drug.getId(), drug.getName(),
                        drug.getSpecialHandling().name(), specialHandlingText(drug.getSpecialHandling())));
            }
        }

        boolean passed = issues.stream().noneMatch(item -> item.level() == IssueLevel.BLOCK);
        return new QcResult.PrescriptionCheck(passed, issues, annotations);
    }

    /** 药名匹配：支持「法半夏」命中「半夏」这类炮制品后缀差异。 */
    private static boolean matches(Set<String> names, String ruleName) {
        for (String name : names) {
            if (name.equals(ruleName) || name.contains(ruleName) || ruleName.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /** 特殊煎法写入用法文案；医生已手写用法时不覆盖，只追加标记。 */
    public static String appendHandling(String usageText, SpecialHandling handling) {
        if (handling == null || handling == SpecialHandling.NORMAL) {
            return usageText;
        }
        String note = specialHandlingText(handling);
        if (note.isEmpty()) {
            return usageText;
        }
        if (usageText == null || usageText.isBlank()) {
            return note;
        }
        return usageText.contains(note) ? usageText : usageText + "；" + note;
    }

    public static String specialHandlingText(SpecialHandling handling) {
        return switch (handling) {
            case PRE_DECOCT -> "先煎 30 分钟";
            case POST_ADD -> "后下（其他药煎好前 5-10 分钟加入）";
            case WRAP_DECOCT -> "包煎";
            case SEPARATE_DECOCT -> "另煎兑服";
            case MELT -> "烊化兑服";
            case TAKE_WITH -> "冲服";
            default -> "";
        };
    }
}
