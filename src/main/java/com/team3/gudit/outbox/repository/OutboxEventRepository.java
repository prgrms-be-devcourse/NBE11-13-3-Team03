package com.team3.gudit.outbox.repository;

import com.team3.gudit.outbox.entity.OutboxEvent;
import com.team3.gudit.outbox.entity.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findAllByStatusOrderByCreatedAtAsc(
            OutboxEventStatus status
    );

    long countByStatus(OutboxEventStatus status);

    @Query(
            value = """
                SELECT COALESCE(
                    EXTRACT(
                        EPOCH FROM (
                            (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                            - MIN(created_at)
                        )
                    )::bigint,
                    0
                )
                FROM outbox_events
                WHERE status = 'PENDING'
                """,
            nativeQuery = true
    )
    long findOldestPendingAgeSeconds();
}