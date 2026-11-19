package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.DecoctionOrder;
import com.fangjian.clinic.domain.enums.DecoctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecoctionOrderRepository extends JpaRepository<DecoctionOrder, Long> {

    @Query("""
            select d from DecoctionOrder d
            where (:status is null or d.status = :status)
              and (:kw = '' or d.decoctionNo like concat('%', :kw, '%')
                   or d.patientName like concat('%', :kw, '%')
                   or d.prescriptionNo like concat('%', :kw, '%'))
            order by d.id desc
            """)
    Page<DecoctionOrder> search(@Param("status") DecoctionStatus status,
                                @Param("kw") String keyword,
                                Pageable pageable);

    long countByStatus(DecoctionStatus status);

    boolean existsByPrescriptionId(Long prescriptionId);
}
