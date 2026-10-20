package com.example.shop.repository;

import com.example.shop.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // All items (products, quantity, price) in one order
    List<OrderItem> findAllByOrderId(Long orderId);

    // True if this product is in any order — used to block deleting that product
    boolean existsByProductId(Long productId);
}
