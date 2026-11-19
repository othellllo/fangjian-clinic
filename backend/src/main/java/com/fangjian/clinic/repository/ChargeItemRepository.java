package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.ChargeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargeItemRepository extends JpaRepository<ChargeItem, Long> {

    List<ChargeItem> findByOrderIdOrderByIdAsc(Long orderId);
}
