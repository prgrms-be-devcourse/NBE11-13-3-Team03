package com.team3.gudit.outbox.consumer;

import com.team3.gudit.sale.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_GROUP;
import static com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_STREAM;
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

    private ObjectMapper objectMapper;
    private StockRestoreConsumer stockRestoreConsumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        stockRestoreConsumer = new StockRestoreConsumer(
                stringRedisTemplate,
                objectMapper,
                inventoryService
        );

        MDC.clear();
    }

    @Test
    @DisplayName("재고 복구 이벤트를 소비하면 재고를 복구하고 ACK 처리한다")
    void consumeAndRestoreStock() {
        // given
        String payload = """
                {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
                """;

        MapRecord<String, String, String> record =
                StreamRecords.newRecord()
                        .in(STOCK_RESTORE_STREAM)
                        .ofMap(Map.of(
                                "eventId", "event-123",
                                "traceId", "trace-123",
                                "eventType", "STOCK_RESTORE_REQUESTED",
                                "payload", payload
                        ))
                        .withId(RecordId.of("1-0"));

        when(stringRedisTemplate.opsForStream())
                .thenReturn(streamOperations);

        when(streamOperations.read(
                any(Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset[].class)
        )).thenReturn(List.of((MapRecord) record));

        // when
        stockRestoreConsumer.consume();

        // then
        verify(inventoryService).restoreStock(
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
        String payload = """
                {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
                """;

        MapRecord<String, String, String> record =
                StreamRecords.newRecord()
                        .in(STOCK_RESTORE_STREAM)
                        .ofMap(Map.of(
                                "eventId", "event-123",
                                "traceId", "trace-123",
                                "eventType", "STOCK_RESTORE_REQUESTED",
                                "payload", payload
                        ))
                        .withId(RecordId.of("1-0"));

        when(stringRedisTemplate.opsForStream())
                .thenReturn(streamOperations);

        when(streamOperations.read(
                any(Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset[].class)
        )).thenReturn(List.of((MapRecord) record));

        doThrow(new RuntimeException("재고 복구 실패"))
                .when(inventoryService)
                .restoreStock(2L, 3L, 1);

        // when
        stockRestoreConsumer.consume();

        // then
        verify(inventoryService).restoreStock(
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
                StreamRecords.newRecord()
                        .in(STOCK_RESTORE_STREAM)
                        .ofMap(Map.of(
                                "eventId", "event-123",
                                "traceId", "trace-123",
                                "eventType", "STOCK_RESTORE_REQUESTED",
                                "payload", "invalid-json"
                        ))
                        .withId(RecordId.of("1-0"));

        when(stringRedisTemplate.opsForStream())
                .thenReturn(streamOperations);

        when(streamOperations.read(
                any(Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset[].class)
        )).thenReturn(List.of((MapRecord) record));

        // when
        stockRestoreConsumer.consume();

        // then
        verify(inventoryService, never())
                .restoreStock(anyLong(), anyLong(), anyInt());

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
        String payload = """
                {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
                """;

        MapRecord<String, String, String> record =
                StreamRecords.newRecord()
                        .in(STOCK_RESTORE_STREAM)
                        .ofMap(Map.of(
                                "eventId", "event-123",
                                "traceId", "trace-123",
                                "eventType", "STOCK_RESTORE_REQUESTED",
                                "payload", payload
                        ))
                        .withId(RecordId.of("1-0"));

        when(stringRedisTemplate.opsForStream())
                .thenReturn(streamOperations);

        when(streamOperations.read(
                any(Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset[].class)
        )).thenReturn(List.of((MapRecord) record));

        // when
        stockRestoreConsumer.consume();

        // then
        assertThat(MDC.get("traceId")).isNull();
    }
}