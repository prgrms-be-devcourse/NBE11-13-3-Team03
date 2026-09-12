package com.team3.gudit.outbox.consumer;

import com.team3.gudit.outbox.dto.StockRestoreEventPayload;
import com.team3.gudit.sale.service.InventoryService;
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

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

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

    private void processRecord(
            StreamOperations<String, Object, Object> streamOperations,
            MapRecord<String, Object, Object> record
    ) {
        String eventId = getValue(record, "eventId");
        String traceId = getValue(record, "traceId");
        String payload = getValue(record, "payload");

        try {
            if (traceId != null && !traceId.isBlank()) {
                MDC.put("traceId", traceId);
            }

            StockRestoreEventPayload eventPayload =
                    objectMapper.readValue(
                            payload,
                            StockRestoreEventPayload.class
                    );

            inventoryService.restoreStock(
                    eventPayload.saleId(),
                    eventPayload.userId(),
                    eventPayload.quantity()
            );

            streamOperations.acknowledge(
                    STOCK_RESTORE_STREAM,
                    STOCK_RESTORE_GROUP,
                    record.getId()
            );

            log.info(
                    "Redis Stream 재고 복구 처리 완료. eventId={}, purchaseId={}, saleId={}, userId={}, quantity={}",
                    eventId,
                    eventPayload.purchaseId(),
                    eventPayload.saleId(),
                    eventPayload.userId(),
                    eventPayload.quantity()
            );
        } catch (RuntimeException e) {
            log.warn(
                    "Redis Stream 재고 복구 처리 실패. eventId={}, recordId={}",
                    eventId,
                    record.getId(),
                    e
            );
        } finally {
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