package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.ChargeOrder;
import com.fangjian.clinic.domain.enums.ChargeStatus;
import com.fangjian.clinic.domain.enums.ChargeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChargeOrderRepository extends JpaRepository<ChargeOrder, Long> {

    @Query("""
            select c from ChargeOrder c
            where (:status is null or c.status = :status)
              and (:type is null or c.type = :type)
              and (:from is null or c.createdAt >= :from)
              and (:to is null or c.createdAt < :to)
            order by c.id desc
            """)
    Page<ChargeOrder> search(@Param("status") ChargeStatus status,
                             @Param("type") ChargeType type,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to,
                             Pageable pageable);

    List<ChargeOrder> findByVisitIdOrderByIdDesc(Long visitId);

    List<ChargeOrder> findByPatientIdOrderByIdDesc(Long patientId);

    Optional<ChargeOrder> findFirstByVisitIdAndType(Long visitId, ChargeType type);
}
