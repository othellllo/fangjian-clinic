package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.MedicalTerm;
import com.fangjian.clinic.domain.enums.TermCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicalTermRepository extends JpaRepository<MedicalTerm, Long> {

    @Query("""
            select t from MedicalTerm t
            where t.category = :category
              and (:kw = '' or t.term like concat('%', :kw, '%') or t.pinyin like concat('%', upper(:kw), '%'))
            order by t.usageCount desc, t.id asc
            """)
    List<MedicalTerm> suggest(@Param("category") TermCategory category,
                              @Param("kw") String keyword,
                              org.springframework.data.domain.Pageable pageable);

    boolean existsByCategoryAndTerm(TermCategory category, String term);
}
