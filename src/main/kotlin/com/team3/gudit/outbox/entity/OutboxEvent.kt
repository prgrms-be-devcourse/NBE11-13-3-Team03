package com.team3.gudit.outbox.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "outbox_events",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_outbox_event_id",
            columnNames = ["event_id"]
        )
    ]
)
@EntityListeners(AuditingEntityListener::class)
class OutboxEvent protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "event_id", nullable = false, length = 36)
    lateinit var eventId: String
        protected set

    @Column(name = "trace_id")
    var traceId: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    lateinit var eventType: OutboxEventType
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    lateinit var payload: String
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    lateinit var status: OutboxEventStatus
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
        protected set

    private constructor(
        traceId: String?,
        eventType: OutboxEventType,
        payload: String
    ) : this() {
        this.eventId = UUID.randomUUID().toString()
        this.traceId = traceId
        this.eventType = eventType
        this.payload = payload
        this.status = OutboxEventStatus.PENDING
    }

    fun markPublished() {
        status = OutboxEventStatus.PUBLISHED
    }

    companion object {
        @JvmStatic
        fun create(
            traceId: String?,
            eventType: OutboxEventType,
            payload: String
        ): OutboxEvent =
            OutboxEvent(
                traceId = traceId,
                eventType = eventType,
                payload = payload
            )
    }
}