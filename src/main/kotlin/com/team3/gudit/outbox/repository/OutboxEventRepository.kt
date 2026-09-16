package com.team3.gudit.outbox.repository

import com.team3.gudit.outbox.entity.OutboxEvent
import com.team3.gudit.outbox.entity.OutboxEventStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {

    fun findAllByStatusOrderByCreatedAtAsc(
        status: OutboxEventStatus
    ): List<OutboxEvent>

    fun countByStatus(status: OutboxEventStatus): Long

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
    fun findOldestPendingAgeSeconds(): Long
}