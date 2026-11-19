package com.yiguan.his.domain;

import com.yiguan.his.domain.enums.TermCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 联想术语（症状/证型/舌象/脉象/治法/方名/西医诊断）。 */
@Entity
@Table(name = "medical_term")
@Getter
@Setter
@NoArgsConstructor
public class MedicalTerm extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TermCategory category;

    @Column(nullable = false, length = 200)
    private String term;

    /** 拼音首字母，支持 pqx → 脾气虚证 */
    @Column(length = 100)
    private String pinyin;

    @Column(length = 300)
    private String note;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    /** SYSTEM 内置 / HISTORY 历史病历提炼 / MANUAL 手工 */
    @Column(nullable = false, length = 20)
    private String source = "SYSTEM";
}
