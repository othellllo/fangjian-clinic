package com.yiguan.his.domain;

import com.yiguan.his.domain.enums.InteractionRuleType;
import com.yiguan.his.domain.enums.IssueLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 用药安全规则：十八反、十九畏、妊娠禁忌。 */
@Entity
@Table(name = "drug_interaction_rule")
@Getter
@Setter
@NoArgsConstructor
public class DrugInteractionRule extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private InteractionRuleType ruleType;

    /** 药品名（与药品字典 name 对应）。 */
    @Column(name = "item_a", nullable = false, length = 50)
    private String itemA;

    /** 配伍的另一味药；妊娠类规则为 null。 */
    @Column(name = "item_b", length = 50)
    private String itemB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueLevel severity;

    @Column(length = 500)
    private String advice;

    @Column(length = 100)
    private String source;
}
