package com.example.shop.repository;

import com.example.shop.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// JpaRepository already gives us save, findById and findAll (with paging)
public interface OrderRepository extends JpaRepository<Order, Long> {

    // All orders of one customer — used to cancel them when the customer is deleted
    List<Order> findAllByCustomerId(Long customerId);

    // One customer's orders, page by page — used by GET /api/orders?customerId=6
    Page<Order> findAllByCustomerId(Long customerId, Pageable pageable);
}
