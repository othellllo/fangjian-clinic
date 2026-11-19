package com.yiguan.his.repository;

import com.yiguan.his.domain.ChargeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargeItemRepository extends JpaRepository<ChargeItem, Long> {

    List<ChargeItem> findByOrderIdOrderByIdAsc(Long orderId);
}
