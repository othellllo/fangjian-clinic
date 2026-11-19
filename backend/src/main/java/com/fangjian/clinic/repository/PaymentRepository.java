package com.fangjian.clinic.repository;

import com.fangjian.clinic.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByIdAsc(Long orderId);

    List<Payment> findByCreatedAtBetweenOrderByIdAsc(LocalDateTime from, LocalDateTime to);
}
