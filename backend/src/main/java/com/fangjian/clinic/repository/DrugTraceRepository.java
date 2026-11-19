package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.DrugTrace;
import com.fangjian.clinic.domain.enums.TraceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DrugTraceRepository extends JpaRepository<DrugTrace, Long> {

    Optional<DrugTrace> findByCode(String code);

    boolean existsByCode(String code);

    @Query("""
            select t from DrugTrace t
            where (:drugId is null or t.drugId = :drugId)
              and (:batchNo is null or t.batchNo = :batchNo)
              and (:status is null or t.status = :status)
              and (:kw = '' or t.code like concat('%', :kw, '%')
                   or t.drugName like concat('%', :kw, '%')
                   or t.patientName like concat('%', :kw, '%'))
            order by t.id desc
            """)
    Page<DrugTrace> search(@Param("drugId") Long drugId,
                           @Param("batchNo") String batchNo,
                           @Param("status") TraceStatus status,
                           @Param("kw") String keyword,
                           Pageable pageable);

    List<DrugTrace> findByPrescriptionIdOrderByIdAsc(Long prescriptionId);

    List<DrugTrace> findByIdIn(Collection<Long> ids);

    long countByStatus(TraceStatus status);
}
