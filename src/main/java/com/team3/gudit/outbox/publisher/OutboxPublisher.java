package com.team3.gudit.outbox.publisher;

import com.team3.gudit.outbox.entity.OutboxEvent;
import com.team3.gudit.outbox.entity.OutboxEventStatus;
import com.team3.gudit.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events =
                outboxEventRepository
                        .findAllByStatusOrderByCreatedAtAsc(
                                OutboxEventStatus.PENDING
                        );

        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            MapRecord<String, String, String> record =
                    StreamRecords
                            .newRecord()
                            .in(
                                    StockRestoreStreamConstants
                                            .STOCK_RESTORE_STREAM
                            )
                            .ofMap(
                                    Map.of(
                                            "eventId",
                                            event.getEventId(),

                                            "traceId",
                                            event.getTraceId() == null
                                                    ? ""
                                                    : event.getTraceId(),

                                            "eventType",
                                            event.getEventType().name(),

                                            "payload",
                                            event.getPayload()
                                    )
                            );

            stringRedisTemplate
                    .opsForStream()
                    .add(record);

            event.markPublished();

            log.info(
                    "Outbox event published. "
                            + "eventId={}, eventType={}",
                    event.getEventId(),
                    event.getEventType()
            );

        } catch (RuntimeException e) {
            log.warn(
                    "Outbox event publish failed. "
                            + "eventId={}, eventType={}",
                    event.getEventId(),
                    event.getEventType(),
                    e
            );
        }
    }
}