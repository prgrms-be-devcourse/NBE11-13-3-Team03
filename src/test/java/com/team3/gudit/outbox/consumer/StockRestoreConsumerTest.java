package com.team3.gudit.outbox.consumer;

import com.team3.gudit.outbox.metrics.RedisStreamMetrics;
import com.team3.gudit.sale.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.time.Duration;

import static com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockRestoreConsumerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private RedisStreamMetrics redisStreamMetrics;

    private ObjectMapper objectMapper;
    private StockRestoreConsumer stockRestoreConsumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        stockRestoreConsumer = new StockRestoreConsumer(
                stringRedisTemplate,
                objectMapper,
                inventoryService,
                redisStreamMetrics
        );

        MDC.clear();
    }

    @Test
    @DisplayName("재고 복구 이벤트를 소비하면 멱등 재고 복구를 실행하고 ACK 처리한다")
    void consumeAndRestoreStock() {
        // given
        MapRecord<String, String, String> record =
                createRecord(
                        "event-123",
                        "trace-123",
                        """
                        {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
                        """
                );

        mockRead(record);

        // when
        stockRestoreConsumer.consume();

        // then
        verify(inventoryService).restoreStockIdempotently(
                "event-123",
                2L,
                3L,
                1
        );

        verify(streamOperations).acknowledge(
                STOCK_RESTORE_STREAM,
                STOCK_RESTORE_GROUP,
                RecordId.of("1-0")
        );
    }

    @Test
    @DisplayName("재고 복구에 실패하면 ACK 처리하지 않는다")
    void doNotAckWhenRestoreFails() {
        // given
        MapRecord<String, String, String> record =
                createRecord(
                        "event-123",
                        "trace-123",
                        """
                        {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
                        """
                );

        mockRead(record);

        doThrow(new RuntimeException("재고 복구 실패"))
                .when(inventoryService)
                .restoreStockIdempotently(
                        "event-123",
                        2L,
                        3L,
                        1
                );

        // when
        stockRestoreConsumer.consume();

        // then
        verify(inventoryService).restoreStockIdempotently(
                "event-123",
                2L,
                3L,
                1
        );

        verify(streamOperations, never()).acknowledge(
                anyString(),
                anyString(),
                any(RecordId.class)
        );
    }

    @Test
    @DisplayName("잘못된 payload면 재고를 복구하지 않고 ACK 처리하지 않는다")
    void doNotAckWhenPayloadIsInvalid() {
        // given
        MapRecord<String, String, String> record =
                createRecord(
                        "event-123",
                        "trace-123",
                        "invalid-json"
                );

        mockRead(record);

        // when
        stockRestoreConsumer.consume();

        // then
        verify(inventoryService, never())
                .restoreStockIdempotently(
                        anyString(),
                        anyLong(),
                        anyLong(),
                        anyInt()
                );

        verify(streamOperations, never()).acknowledge(
                anyString(),
                anyString(),
                any(RecordId.class)
        );
    }

    @Test
    @DisplayName("처리 후 MDC의 traceId를 제거한다")
    void clearTraceIdAfterProcessing() {
        // given
        MapRecord<String, String, String> record =
                createRecord(
                        "event-123",
                        "trace-123",
                        """
                        {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
                        """
                );

        mockRead(record);

        // when
        stockRestoreConsumer.consume();

        // then
        assertThat(MDC.get("traceId")).isNull();
    }

    private MapRecord<String, String, String> createRecord(
            String eventId,
            String traceId,
            String payload
    ) {
        return StreamRecords.newRecord()
                .in(STOCK_RESTORE_STREAM)
                .ofMap(Map.of(
                        "eventId", eventId,
                        "traceId", traceId,
                        "eventType", "STOCK_RESTORE_REQUESTED",
                        "payload", payload
                ))
                .withId(RecordId.of("1-0"));
    }

    @Test
    @DisplayName("오래된 Pending 메시지를 claim하여 재처리하고 ACK 처리한다")
    void retryPendingMessage() {
        // given
        MapRecord<String, String, String> record =
                createRecord(
                        "event-123",
                        "trace-123",
                        """
                        {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
                        """
                );

        PendingMessage pendingMessage = mock(PendingMessage.class);

        when(pendingMessage.getId())
                .thenReturn(RecordId.of("1-0"));

        PendingMessages pendingMessages =
                mock(PendingMessages.class);

        when(pendingMessages.isEmpty())
                .thenReturn(false);

        when(pendingMessages.iterator())
                .thenReturn(List.of(pendingMessage).iterator());

        when(stringRedisTemplate.opsForStream())
                .thenReturn(streamOperations);

        when(streamOperations.pending(
                eq(STOCK_RESTORE_STREAM),
                eq(STOCK_RESTORE_GROUP),
                any(Range.class),
                anyLong(),
                any(Duration.class)
        )).thenReturn(pendingMessages);

        when(streamOperations.claim(
                eq(STOCK_RESTORE_STREAM),
                eq(STOCK_RESTORE_GROUP),
                eq(STOCK_RESTORE_CONSUMER),
                any(Duration.class),
                eq(RecordId.of("1-0"))
        )).thenReturn(List.of((MapRecord) record));

        // when
        stockRestoreConsumer.retryPending();

        // then
        verify(inventoryService).restoreStockIdempotently(
                "event-123",
                2L,
                3L,
                1
        );

        verify(streamOperations).acknowledge(
                STOCK_RESTORE_STREAM,
                STOCK_RESTORE_GROUP,
                RecordId.of("1-0")
        );
    }

    @Test
    @DisplayName("Pending 메시지 재처리에 실패하면 ACK 처리하지 않는다")
    void doNotAckWhenPendingRetryFails() {
        // given
        MapRecord<String, String, String> record =
                createRecord(
                        "event-123",
                        "trace-123",
                        """
                        {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
                        """
                );

        PendingMessage pendingMessage = mock(PendingMessage.class);

        when(pendingMessage.getId())
                .thenReturn(RecordId.of("1-0"));

        PendingMessages pendingMessages =
                mock(PendingMessages.class);

        when(pendingMessages.isEmpty())
                .thenReturn(false);

        when(pendingMessages.iterator())
                .thenReturn(List.of(pendingMessage).iterator());

        when(stringRedisTemplate.opsForStream())
                .thenReturn(streamOperations);

        when(streamOperations.pending(
                eq(STOCK_RESTORE_STREAM),
                eq(STOCK_RESTORE_GROUP),
                any(Range.class),
                anyLong(),
                any(Duration.class)
        )).thenReturn(pendingMessages);

        when(streamOperations.claim(
                eq(STOCK_RESTORE_STREAM),
                eq(STOCK_RESTORE_GROUP),
                eq(STOCK_RESTORE_CONSUMER),
                any(Duration.class),
                eq(RecordId.of("1-0"))
        )).thenReturn(List.of((MapRecord) record));

        doThrow(new RuntimeException("재처리 실패"))
                .when(inventoryService)
                .restoreStockIdempotently(
                        "event-123",
                        2L,
                        3L,
                        1
                );

        // when
        stockRestoreConsumer.retryPending();

        // then
        verify(streamOperations, never()).acknowledge(
                anyString(),
                anyString(),
                any(RecordId.class)
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockRead(
            MapRecord<String, String, String> record
    ) {
        when(stringRedisTemplate.opsForStream())
                .thenReturn(streamOperations);

        when(streamOperations.read(
                any(Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset[].class)
        )).thenReturn(List.of((MapRecord) record));
    }
}