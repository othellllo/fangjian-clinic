package com.yiguan.his.repository;

import com.yiguan.his.domain.InsuranceSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsuranceSettlementRepository extends JpaRepository<InsuranceSettlement, Long> {

    List<InsuranceSettlement> findByOrderIdOrderByIdDesc(Long orderId);
}
