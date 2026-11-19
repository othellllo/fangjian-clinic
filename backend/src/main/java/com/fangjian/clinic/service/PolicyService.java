package com.fangjian.clinic.service;

import com.fangjian.clinic.common.BusinessException;
import com.fangjian.clinic.common.PageResult;
import com.fangjian.clinic.domain.InsurancePolicy;
import com.fangjian.clinic.domain.enums.PolicyCategory;
import com.fangjian.clinic.domain.enums.PolicyStatus;
import com.fangjian.clinic.dto.PolicyDto;
import com.fangjian.clinic.repository.InsurancePolicyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 医保政策库：登记、检索、到期提醒。
 *
 * <p>系统不自动抓取政策（官方原文为准），只做结构化登记与提醒；
 * 政策变更需要同步调整的参数在摘要里列明，避免"只改文档不改配置"。</p>
 */
@Service
public class PolicyService {

    private final InsurancePolicyRepository repository;

    public PolicyService(InsurancePolicyRepository repository) {
        this.repository = repository;
    }

    public PageResult<PolicyDto> page(PolicyCategory category, PolicyStatus status, String keyword,
                                      int page, int size) {
        Page<InsurancePolicy> result = repository.search(category, status,
                keyword == null ? "" : keyword.trim(),
                PageRequest.of(Math.max(page - 1, 0), size));
        return PageResult.of(result, PolicyService::toDto);
    }

    public PolicyDto get(Long id) {
        return toDto(find(id));
    }

    public InsurancePolicy find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("政策"));
    }

    @Transactional
    public PolicyDto create(PolicyRequest request) {
        validate(request);
        InsurancePolicy policy = new InsurancePolicy();
        apply(policy, request);
        return toDto(repository.save(policy));
    }

    @Transactional
    public PolicyDto update(Long id, PolicyRequest request) {
        InsurancePolicy policy = find(id);
        apply(policy, request);
        return toDto(repository.save(policy));
    }

    @Transactional
    public void delete(Long id) {
        InsurancePolicy policy = find(id);
        // 不物理删库：政策是审计依据，改为作废（失效）
        policy.setStatus(PolicyStatus.EXPIRED);
        policy.setSummary((policy.getSummary() == null ? "" : policy.getSummary() + " ") + "（已作废）");
        repository.save(policy);
    }

    /** 生效前 30 天与失效前 30 天的政策提醒。 */
    public Map<String, Object> reminders() {
        LocalDate today = LocalDate.now();
        List<PolicyDto> effectiveSoon = repository
                .findByEffectiveDateBetweenOrderByEffectiveDateAsc(today, today.plusDays(30))
                .stream().map(PolicyService::toDto).toList();
        List<PolicyDto> expireSoon = repository
                .findByExpireDateBetweenOrderByExpireDateAsc(today, today.plusDays(30))
                .stream().map(PolicyService::toDto).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("effectiveSoon", effectiveSoon);
        data.put("expireSoon", expireSoon);
        data.put("today", today.toString());
        List<String> hints = new ArrayList<>();
        if (!effectiveSoon.isEmpty()) {
            hints.add("有 " + effectiveSoon.size() + " 项政策将在 30 天内生效，请提前调整收费参数与操作流程");
        }
        if (!expireSoon.isEmpty()) {
            hints.add("有 " + expireSoon.size() + " 项政策将在 30 天内失效，请确认替代文件");
        }
        data.put("hints", hints);
        return data;
    }

    private void validate(PolicyRequest request) {
        if (!StringUtils.hasText(request.title())) {
            throw new BusinessException("政策标题不能为空");
        }
        if (request.category() == null) {
            throw new BusinessException("请选择政策分类");
        }
        if (request.effectiveDate() != null && request.expireDate() != null
                && request.expireDate().isBefore(request.effectiveDate())) {
            throw new BusinessException("失效日期不能早于生效日期");
        }
    }

    private void apply(InsurancePolicy policy, PolicyRequest request) {
        policy.setTitle(request.title().trim());
        policy.setIssuer(request.issuer());
        policy.setDocNo(request.docNo());
        policy.setCategory(request.category());
        policy.setPublishDate(request.publishDate());
        policy.setEffectiveDate(request.effectiveDate());
        policy.setExpireDate(request.expireDate());
        policy.setSummary(request.summary());
        // 正文可能很长，截断到字段长度，完整原文以来源链接为准
        String content = request.content();
        policy.setContent(content != null && content.length() > 20000 ? content.substring(0, 20000) : content);
        policy.setSourceUrl(request.sourceUrl());
        policy.setStatus(resolveStatus(request, policy));
    }

    private PolicyStatus resolveStatus(PolicyRequest request, InsurancePolicy policy) {
        LocalDate today = LocalDate.now();
        if (policy.getExpireDate() != null && policy.getExpireDate().isBefore(today)) {
            return PolicyStatus.EXPIRED;
        }
        if (request.status() != null) {
            return request.status();
        }
        if (policy.getEffectiveDate() != null) {
            return policy.getEffectiveDate().isAfter(today) ? PolicyStatus.DRAFT : PolicyStatus.EFFECTIVE;
        }
        return PolicyStatus.DRAFT;
    }

    static PolicyDto toDto(InsurancePolicy policy) {
        return new PolicyDto(policy.getId(), policy.getTitle(), policy.getIssuer(), policy.getDocNo(),
                policy.getCategory(), policy.getPublishDate(), policy.getEffectiveDate(), policy.getExpireDate(),
                policy.getSummary(), policy.getContent(), policy.getSourceUrl(), policy.getStatus(),
                policy.getCreatedAt());
    }

    /** 政策新增/编辑请求。 */
    public record PolicyRequest(String title, String issuer, String docNo, PolicyCategory category,
                                LocalDate publishDate, LocalDate effectiveDate, LocalDate expireDate,
                                String summary, String content, String sourceUrl, PolicyStatus status) {
    }
}
