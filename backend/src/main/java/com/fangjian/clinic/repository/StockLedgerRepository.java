package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.StockLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLedgerRepository extends JpaRepository<StockLedger, Long> {

    Page<StockLedger> findByDrugIdOrderByIdDesc(Long drugId, Pageable pageable);

    Page<StockLedger> findAllByOrderByIdDesc(Pageable pageable);
}
