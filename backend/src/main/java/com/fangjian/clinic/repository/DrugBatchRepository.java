package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.DrugBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DrugBatchRepository extends JpaRepository<DrugBatch, Long> {

    List<DrugBatch> findByDrugIdOrderByExpiryDateAsc(Long drugId);

    Optional<DrugBatch> findByDrugIdAndBatchNo(Long drugId, String batchNo);

    /** 按效期先后发药（先进先出 / 近效期先出）。 */
    @Query("""
            select b from DrugBatch b
            where b.drugId = :drugId and b.quantity > 0
            order by b.expiryDate asc, b.id asc
            """)
    List<DrugBatch> findAvailableBatches(@Param("drugId") Long drugId);

    @Query("select coalesce(sum(b.quantity), 0) from DrugBatch b where b.drugId = :drugId")
    BigDecimal sumQuantityByDrugId(@Param("drugId") Long drugId);

    @Query("""
            select b from DrugBatch b
            where b.quantity > 0
              and b.expiryDate is not null
              and b.expiryDate <= :before
            order by b.expiryDate asc
            """)
    List<DrugBatch> findExpiringBefore(@Param("before") LocalDate before);
}
