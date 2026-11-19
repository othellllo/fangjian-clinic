package com.yiguan.his.service;

import com.yiguan.his.domain.enums.IssueLevel;
import com.yiguan.his.domain.enums.RecordType;
import com.yiguan.his.dto.MedicalRecordRequest;
import com.yiguan.his.dto.QcResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 病历质控：按《中医病历书写基本规范》（国中医药医政发〔2010〕29 号）
 * 门诊病历要求校验必填项与基本逻辑。
 *
 * <p>BLOCK 表示不允许保存（缺核心内容），WARN 提示可保存但建议完善。</p>
 */
@Service
public class RecordQcService {

    public QcResult check(MedicalRecordRequest request) {
        RecordType type = request.recordType() == null ? RecordType.TCM : request.recordType();
        List<QcResult.Issue> issues = new ArrayList<>();

        // 公共必填
        require(issues, request.chiefComplaint(), "chiefComplaint", "主诉", 50);
        require(issues, request.presentIllness(), "presentIllness", "现病史", 1000);
        if (StringUtils.hasText(request.chiefComplaint()) && !hasDuration(request.chiefComplaint())) {
            issues.add(issue(IssueLevel.WARN, "CHIEF_COMPLAINT_DURATION", "chiefComplaint",
                    "主诉未体现持续时间", "规范要求主诉包含主要症状及持续时间，例如「乏力纳差 2 周」"));
        }
        if (StringUtils.hasText(request.presentIllness()) && request.presentIllness().trim().length() < 10) {
            issues.add(issue(IssueLevel.BLOCK, "PRESENT_ILLNESS_SHORT", "presentIllness",
                    "现病史过短", "现病史应包含发病情况、症状演变、诊疗经过与一般情况"));
        } else if (StringUtils.hasText(request.presentIllness()) && request.presentIllness().trim().length() < 20) {
            issues.add(issue(IssueLevel.WARN, "PRESENT_ILLNESS_BRIEF", "presentIllness",
                    "现病史较简略", "建议补充发病诱因、伴随症状、诊治经过"));
        }
        if (!StringUtils.hasText(request.pastHistory())) {
            issues.add(issue(IssueLevel.BLOCK, "PAST_HISTORY_MISSING", "pastHistory",
                    "既往史未填写", "无特殊病史也应写明「无」或「否认」"));
        }
        if (!StringUtils.hasText(request.advice())) {
            issues.add(issue(IssueLevel.WARN, "ADVICE_MISSING", "advice",
                    "处理意见/医嘱未填写", "门诊病历应有治疗处理意见或医嘱"));
        }

        if (type == RecordType.TCM) {
            require(issues, request.inspection(), "inspection", "望诊", 1000);
            require(issues, request.palpation(), "palpation", "切诊", 1000);
            require(issues, request.tongue(), "tongue", "舌象", 500);
            require(issues, request.pulse(), "pulse", "脉象", 500);
            require(issues, request.tcmDisease(), "tcmDisease", "中医病名", 200);
            require(issues, request.syndrome(), "syndrome", "证候诊断", 500);
            require(issues, request.treatmentPrinciple(), "treatmentPrinciple", "治则治法", 500);
            if (!StringUtils.hasText(request.auscultation())) {
                issues.add(issue(IssueLevel.WARN, "AUSCULTATION_MISSING", "auscultation",
                        "闻诊未填写", "四诊应齐全，无异常可写「未闻及异常」"));
            }
            if (!StringUtils.hasText(request.inquiry())) {
                issues.add(issue(IssueLevel.WARN, "INQUIRY_MISSING", "inquiry",
                        "问诊未填写", "建议按十问记录寒热、饮食、睡眠、二便等"));
            }
            if (StringUtils.hasText(request.syndrome()) && StringUtils.hasText(request.treatmentPrinciple())
                    && !matchesSyndrome(request.syndrome(), request.treatmentPrinciple())) {
                issues.add(issue(IssueLevel.INFO, "SYNDROME_TREATMENT", "treatmentPrinciple",
                        "证型与治则未明显对应", "请核对「辨证-治法-方药」是否一致"));
            }
        } else {
            require(issues, request.physicalExam(), "physicalExam", "体格检查", 2000);
            require(issues, request.westernDiagnosis(), "westernDiagnosis", "西医诊断", 500);
            if (!StringUtils.hasText(request.auxiliaryExam())) {
                issues.add(issue(IssueLevel.WARN, "AUXILIARY_EXAM_MISSING", "auxiliaryExam",
                        "辅助检查未填写", "无检查也应写明「暂缺」或「无」"));
            }
            if (StringUtils.hasText(request.westernDiagnosis()) && !StringUtils.hasText(request.icdCode())) {
                issues.add(issue(IssueLevel.INFO, "ICD_MISSING", "icdCode",
                        "诊断未填 ICD-10 编码", "电子病历建议同时登记 ICD-10 编码"));
            }
        }

        boolean passed = issues.stream().noneMatch(item -> item.level() == IssueLevel.BLOCK);
        return new QcResult(passed, issues);
    }

    private void require(List<QcResult.Issue> issues, String value, String field, String label, int maxLength) {
        if (!StringUtils.hasText(value)) {
            issues.add(issue(IssueLevel.BLOCK, "REQUIRED_MISSING", field, label + "未填写", label + "为必填项"));
        } else if (value.length() > maxLength) {
            issues.add(issue(IssueLevel.WARN, "TOO_LONG", field, label + "过长",
                    label + "建议不超过 " + maxLength + " 字"));
        }
    }

    /** 主诉是否包含持续时间（如 2周、3天、半年）。 */
    static boolean hasDuration(String complaint) {
        return complaint.matches(".*(\\d+|[一二三四五六七八九十半两几]+)\\s*(天|日|周|星期|月|年|小时|分钟|年余|月余).*");
    }

    /** 证型与治则的粗匹配（同字/同义片段）。 */
    static boolean matchesSyndrome(String syndrome, String treatment) {
        String[] keys = {"气虚", "阳虚", "阴虚", "血虚", "气滞", "血瘀", "痰", "湿", "热", "寒", "肝", "脾", "肾", "心", "肺"};
        for (String key : keys) {
            if (syndrome.contains(key) && treatment.contains(key)) {
                return true;
            }
        }
        // 单独映射：气虚 → 益气/补气，脾虚 → 健脾
        if (syndrome.contains("气虚") && (treatment.contains("益气") || treatment.contains("补气") || treatment.contains("健脾"))) {
            return true;
        }
        if (syndrome.contains("肝郁") && (treatment.contains("疏肝") || treatment.contains("解郁"))) {
            return true;
        }
        if (syndrome.contains("阴虚") && (treatment.contains("滋阴") || treatment.contains("养阴"))) {
            return true;
        }
        if (syndrome.contains("痰") && (treatment.contains("化痰") || treatment.contains("祛痰"))) {
            return true;
        }
        return false;
    }

    private QcResult.Issue issue(IssueLevel level, String type, String field, String message, String advice) {
        return new QcResult.Issue(level, type, field, message, advice);
    }
}
