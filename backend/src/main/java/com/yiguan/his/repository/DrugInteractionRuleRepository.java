package com.yiguan.his.repository;

import com.yiguan.his.domain.DrugInteractionRule;
import com.yiguan.his.domain.enums.InteractionRuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrugInteractionRuleRepository extends JpaRepository<DrugInteractionRule, Long> {

    List<DrugInteractionRule> findByRuleType(InteractionRuleType ruleType);

    List<DrugInteractionRule> findByRuleTypeIn(List<InteractionRuleType> ruleTypes);
}
