package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.InsurancePolicy;
import com.fangjian.clinic.domain.enums.PolicyCategory;
import com.fangjian.clinic.domain.enums.PolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {

    @Query("""
            select p from InsurancePolicy p
            where (:category is null or p.category = :category)
              and (:status is null or p.status = :status)
              and (:kw = '' or p.title like concat('%', :kw, '%')
                   or p.docNo like concat('%', :kw, '%')
                   or p.summary like concat('%', :kw, '%'))
            order by p.effectiveDate desc, p.id desc
            """)
    Page<InsurancePolicy> search(@Param("category") PolicyCategory category,
                                 @Param("status") PolicyStatus status,
                                 @Param("kw") String keyword,
                                 Pageable pageable);

    List<InsurancePolicy> findByEffectiveDateBetweenOrderByEffectiveDateAsc(LocalDate from, LocalDate to);

    List<InsurancePolicy> findByExpireDateBetweenOrderByExpireDateAsc(LocalDate from, LocalDate to);
}
