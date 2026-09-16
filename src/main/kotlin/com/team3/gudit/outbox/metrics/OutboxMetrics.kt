package com.team3.gudit.outbox.metrics

import com.team3.gudit.outbox.entity.OutboxEventStatus
import com.team3.gudit.outbox.repository.OutboxEventRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class OutboxMetrics(
    private val outboxEventRepository: OutboxEventRepository,
    meterRegistry: MeterRegistry
) {

    private val pendingCount = AtomicLong()
    private val oldestPendingAgeSeconds = AtomicLong()

    init {
        Gauge.builder(
            "gudit.outbox.pending.count",
            pendingCount
        ) { it.get().toDouble() }
            .description("Number of pending outbox events")
            .register(meterRegistry)

        Gauge.builder(
            "gudit.outbox.oldest.pending.age.seconds",
            oldestPendingAgeSeconds
        ) { it.get().toDouble() }
            .description("Age of the oldest pending outbox event")
            .register(meterRegistry)
    }

    @Scheduled(
        initialDelay = 0,
        fixedDelay = 5000
    )
    fun refreshOutboxMetrics() {
        val count = outboxEventRepository.countByStatus(
            OutboxEventStatus.PENDING
        )

        pendingCount.set(count)

        val oldestAge =
            outboxEventRepository.findOldestPendingAgeSeconds()

        oldestPendingAgeSeconds.set(
            maxOf(oldestAge, 0L)
        )
    }
}