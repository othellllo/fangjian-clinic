package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.DrugInteractionRule;
import com.fangjian.clinic.domain.enums.InteractionRuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrugInteractionRuleRepository extends JpaRepository<DrugInteractionRule, Long> {

    List<DrugInteractionRule> findByRuleType(InteractionRuleType ruleType);

    List<DrugInteractionRule> findByRuleTypeIn(List<InteractionRuleType> ruleTypes);
}
