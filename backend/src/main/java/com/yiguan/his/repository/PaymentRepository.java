package com.yiguan.his.repository;

import com.yiguan.his.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByIdAsc(Long orderId);

    List<Payment> findByCreatedAtBetweenOrderByIdAsc(LocalDateTime from, LocalDateTime to);
}
