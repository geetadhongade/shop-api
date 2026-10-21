package com.example.shop.repository;

import com.example.shop.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

// The publisher's "read waiting events" query is added in the publisher step
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
