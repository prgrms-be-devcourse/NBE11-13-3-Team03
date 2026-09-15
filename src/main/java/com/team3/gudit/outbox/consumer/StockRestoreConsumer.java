package com.team3.gudit.outbox.consumer;

import com.team3.gudit.outbox.dto.StockRestoreEventPayload;
import com.team3.gudit.outbox.metrics.RedisStreamMetrics;
import com.team3.gudit.sale.service.InventoryService;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockRestoreConsumer {

    private static final Duration PENDING_MIN_IDLE = Duration.ofSeconds(30);
    private static final long PENDING_BATCH_SIZE = 10;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;
    private final RedisStreamMetrics redisStreamMetrics;

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        StreamOperations<String, Object, Object> streamOperations =
                stringRedisTemplate.opsForStream();

        List<MapRecord<String, Object, Object>> records =
                streamOperations.read(
                        Consumer.from(
                                STOCK_RESTORE_GROUP,
                                STOCK_RESTORE_CONSUMER
                        ),
                        StreamReadOptions.empty()
                                .count(10)
                                .block(Duration.ofSeconds(1)),
                        StreamOffset.create(
                                STOCK_RESTORE_STREAM,
                                ReadOffset.lastConsumed()
                        )
                );

        if (records == null || records.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> record : records) {
            processRecord(streamOperations, record);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void retryPending() {
        StreamOperations<String, Object, Object> streamOperations =
                stringRedisTemplate.opsForStream();

        var pendingMessages = streamOperations.pending(
                STOCK_RESTORE_STREAM,
                STOCK_RESTORE_GROUP,
                org.springframework.data.domain.Range.unbounded(),
                PENDING_BATCH_SIZE,
                PENDING_MIN_IDLE
        );

        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }

        for (var pendingMessage : pendingMessages) {
            List<MapRecord<String, Object, Object>> claimedRecords =
                    streamOperations.claim(
                            STOCK_RESTORE_STREAM,
                            STOCK_RESTORE_GROUP,
                            STOCK_RESTORE_CONSUMER,
                            PENDING_MIN_IDLE,
                            pendingMessage.getId()
                    );

            if (claimedRecords == null || claimedRecords.isEmpty()) {
                continue;
            }

            for (MapRecord<String, Object, Object> record : claimedRecords) {
                redisStreamMetrics.recordStockRestoreRetry();
                processRecord(streamOperations, record);
            }
        }
    }

    private void processRecord(
            StreamOperations<String, Object, Object> streamOperations,
            MapRecord<String, Object, Object> record
    ) {
        String eventId = getValue(record, "eventId");
        String traceId = getValue(record, "traceId");
        String payload = getValue(record, "payload");

        Timer.Sample sample =
                redisStreamMetrics.startConsumerProcessingTimer();

        String processingResult = "failed";

        try {
            if (traceId != null && !traceId.isBlank()) {
                MDC.put("traceId", traceId);
            }

            StockRestoreEventPayload eventPayload =
                    objectMapper.readValue(
                            payload,
                            StockRestoreEventPayload.class
                    );

            inventoryService.restoreStockIdempotently(
                    eventId,
                    eventPayload.saleId(),
                    eventPayload.userId(),
                    eventPayload.quantity()
            );

            streamOperations.acknowledge(
                    STOCK_RESTORE_STREAM,
                    STOCK_RESTORE_GROUP,
                    record.getId()
            );

            processingResult = "success";

            log.info(
                    "Redis Stream 재고 복구 처리 완료. eventId={}, purchaseId={}, saleId={}, userId={}, quantity={}",
                    eventId,
                    eventPayload.purchaseId(),
                    eventPayload.saleId(),
                    eventPayload.userId(),
                    eventPayload.quantity()
            );
        } catch (RuntimeException e) {
            redisStreamMetrics.recordStockRestoreProcessingFailure();

            log.warn(
                    "Redis Stream 재고 복구 처리 실패. eventId={}, recordId={}",
                    eventId,
                    record.getId(),
                    e
            );
        } finally {
            redisStreamMetrics.recordConsumerProcessingTime(
                    sample,
                    "stock_restore",
                    processingResult
            );

            MDC.remove("traceId");
        }
    }

    private String getValue(
            MapRecord<String, Object, Object> record,
            String key
    ) {
        Object value = record.getValue().get(key);
        return value == null ? null : value.toString();
    }
}