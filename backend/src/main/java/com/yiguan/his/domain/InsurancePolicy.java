package com.yiguan.his.domain;

import com.yiguan.his.domain.enums.PolicyCategory;
import com.yiguan.his.domain.enums.PolicyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 医保政策库条目。
 *
 * <p>设计取舍：不让系统自动抓取政策（版本错漏与断章取义风险高），
 * 而是结构化登记 + 生效/失效日期 + 到期提醒，正文以官方链接为准。</p>
 */
@Entity
@Table(name = "insurance_policy")
@Getter
@Setter
@NoArgsConstructor
public class InsurancePolicy extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    /** 发文机关。 */
    @Column(length = 100)
    private String issuer;

    /** 文号。 */
    @Column(name = "doc_no", length = 100)
    private String docNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyCategory category;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expire_date")
    private LocalDate expireDate;

    @Column(length = 1000)
    private String summary;

    /** 正文摘录（完整原文建议用来源链接）。 */
    @Column(length = 20000)
    private String content;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status = PolicyStatus.DRAFT;
}
