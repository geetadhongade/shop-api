package com.example.shop.repository;

import com.example.shop.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // The oldest NEW events, locked until the transaction ends.
    // SKIP LOCKED: rows another copy of the app is already sending are skipped, never sent twice at once.
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE status = 'NEW'
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findNextToPublish(@Param("limit") int limit);
}
