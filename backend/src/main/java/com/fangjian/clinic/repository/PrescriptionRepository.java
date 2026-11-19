package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.Prescription;
import com.fangjian.clinic.domain.enums.ChargeStatus;
import com.fangjian.clinic.domain.enums.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByVisitIdOrderByIdDesc(Long visitId);

    List<Prescription> findByStatus(PrescriptionStatus status);

    @Query("""
            select p from Prescription p
            where p.status = :status
              and exists (select 1 from ChargeOrder c where c.id = p.chargeOrderId and c.status = :chargeStatus)
            order by p.id desc
            """)
    List<Prescription> findDispensable(@Param("status") PrescriptionStatus status,
                                       @Param("chargeStatus") ChargeStatus chargeStatus);

    long countByCreatedAtBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);
}
