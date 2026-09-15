package com.team3.gudit.outbox.metrics;

import com.team3.gudit.outbox.entity.OutboxEventStatus;
import com.team3.gudit.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class OutboxMetrics {

    private final OutboxEventRepository outboxEventRepository;
    private final AtomicLong pendingCount = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();

    public OutboxMetrics(
            OutboxEventRepository outboxEventRepository,
            MeterRegistry meterRegistry
    ) {
        this.outboxEventRepository = outboxEventRepository;

        Gauge.builder(
                        "gudit.outbox.pending.count",
                        pendingCount,
                        AtomicLong::get
                )
                .description("Number of pending outbox events")
                .register(meterRegistry);

        Gauge.builder(
                        "gudit.outbox.oldest.pending.age.seconds",
                        oldestPendingAgeSeconds,
                        AtomicLong::get
                )
                .description("Age of the oldest pending outbox event")
                .register(meterRegistry);
    }

    @Scheduled(
            initialDelay = 0,
            fixedDelay = 5000
    )
    public void refreshOutboxMetrics() {
        long count = outboxEventRepository.countByStatus(
                OutboxEventStatus.PENDING
        );

        pendingCount.set(count);

        long oldestAge =
                outboxEventRepository.findOldestPendingAgeSeconds();

        oldestPendingAgeSeconds.set(
                Math.max(oldestAge, 0L)
        );
    }
}