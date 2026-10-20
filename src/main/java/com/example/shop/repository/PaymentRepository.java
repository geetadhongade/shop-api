package com.example.shop.repository;

import com.example.shop.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Used to block a second payment for the same order
    boolean existsByOrderId(Long orderId);

    // The payment for one order (if it has been paid)
    Optional<Payment> findByOrderId(Long orderId);
}
