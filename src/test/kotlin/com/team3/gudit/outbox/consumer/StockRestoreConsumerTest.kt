package com.team3.gudit.outbox.consumer

import com.team3.gudit.outbox.metrics.RedisStreamMetrics
import com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_CONSUMER
import com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_GROUP
import com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_STREAM
import com.team3.gudit.sale.service.InventoryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.slf4j.MDC
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.PendingMessage
import org.springframework.data.redis.connection.stream.PendingMessages
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@ExtendWith(MockitoExtension::class)
class StockRestoreConsumerTest {

    @Mock
    lateinit var stringRedisTemplate: StringRedisTemplate

    @Mock
    lateinit var streamOperations: StreamOperations<String, Any, Any>

    @Mock
    lateinit var inventoryService: InventoryService

    @Mock
    lateinit var redisStreamMetrics: RedisStreamMetrics

    private lateinit var objectMapper: ObjectMapper
    private lateinit var stockRestoreConsumer: StockRestoreConsumer

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper()

        stockRestoreConsumer = StockRestoreConsumer(
            stringRedisTemplate,
            objectMapper,
            inventoryService,
            redisStreamMetrics
        )

        MDC.clear()
    }

    @Test
    @DisplayName("재고 복구 이벤트를 소비하면 멱등 재고 복구를 실행하고 ACK 처리한다")
    fun consumeAndRestoreStock() {
        // given
        val record = createRecord(
            eventId = "event-123",
            traceId = "trace-123",
            payload = """
                {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
            """.trimIndent()
        )

        mockRead(record)

        // when
        stockRestoreConsumer.consume()

        // then
        Mockito.verify(inventoryService)
            .restoreStockIdempotently(
                "event-123",
                2L,
                3L,
                1
            )

        Mockito.verify(streamOperations)
            .acknowledge(
                STOCK_RESTORE_STREAM,
                STOCK_RESTORE_GROUP,
                RecordId.of("1-0")
            )
    }

    @Test
    @DisplayName("재고 복구에 실패하면 ACK 처리하지 않는다")
    fun doNotAckWhenRestoreFails() {
        // given
        val record = createRecord(
            eventId = "event-123",
            traceId = "trace-123",
            payload = """
                {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
            """.trimIndent()
        )

        mockRead(record)

        Mockito.doThrow(
            RuntimeException("재고 복구 실패")
        ).`when`(inventoryService)
            .restoreStockIdempotently(
                "event-123",
                2L,
                3L,
                1
            )

        // when
        stockRestoreConsumer.consume()

        // then
        Mockito.verify(inventoryService)
            .restoreStockIdempotently(
                "event-123",
                2L,
                3L,
                1
            )

        Mockito.verify(
            streamOperations,
            Mockito.never()
        ).acknowledge(
            anyString(),
            anyString(),
            any(RecordId::class.java)
        )
    }

    @Test
    @DisplayName("잘못된 payload면 재고를 복구하지 않고 ACK 처리하지 않는다")
    fun doNotAckWhenPayloadIsInvalid() {
        // given
        val record = createRecord(
            eventId = "event-123",
            traceId = "trace-123",
            payload = "invalid-json"
        )

        mockRead(record)

        // when
        stockRestoreConsumer.consume()

        // then
        Mockito.verify(
            inventoryService,
            Mockito.never()
        ).restoreStockIdempotently(
            anyString(),
            anyLong(),
            anyLong(),
            anyInt()
        )

        Mockito.verify(
            streamOperations,
            Mockito.never()
        ).acknowledge(
            anyString(),
            anyString(),
            any(RecordId::class.java)
        )
    }

    @Test
    @DisplayName("처리 후 MDC의 traceId를 제거한다")
    fun clearTraceIdAfterProcessing() {
        // given
        val record = createRecord(
            eventId = "event-123",
            traceId = "trace-123",
            payload = """
                {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
            """.trimIndent()
        )

        mockRead(record)

        // when
        stockRestoreConsumer.consume()

        // then
        assertThat(MDC.get("traceId")).isNull()
    }

    @Test
    @DisplayName("오래된 Pending 메시지를 claim하여 재처리하고 ACK 처리한다")
    fun retryPendingMessage() {
        // given
        val record = createRecord(
            eventId = "event-123",
            traceId = "trace-123",
            payload = """
                {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
            """.trimIndent()
        )

        val pendingMessage =
            Mockito.mock(PendingMessage::class.java)

        Mockito.`when`(pendingMessage.id)
            .thenReturn(RecordId.of("1-0"))

        val pendingMessages =
            Mockito.mock(PendingMessages::class.java)

        Mockito.`when`(pendingMessages.isEmpty)
            .thenReturn(false)

        Mockito.`when`(pendingMessages.iterator())
            .thenReturn(
                mutableListOf(pendingMessage).iterator()
            )

        Mockito.`when`(
            stringRedisTemplate.opsForStream<Any, Any>()
        ).thenReturn(streamOperations)

        Mockito.`when`(
            streamOperations.pending(
                eq(STOCK_RESTORE_STREAM),
                eq(STOCK_RESTORE_GROUP),
                any<Range<String>>(),
                anyLong(),
                any(Duration::class.java)
            )
        ).thenReturn(pendingMessages)

        Mockito.`when`(
            streamOperations.claim(
                eq(STOCK_RESTORE_STREAM),
                eq(STOCK_RESTORE_GROUP),
                eq(STOCK_RESTORE_CONSUMER),
                any(Duration::class.java),
                eq(RecordId.of("1-0"))
            )
        ).thenReturn(
            listOf(recordAsAny(record))
        )

        // when
        stockRestoreConsumer.retryPending()

        // then
        Mockito.verify(inventoryService)
            .restoreStockIdempotently(
                "event-123",
                2L,
                3L,
                1
            )

        Mockito.verify(streamOperations)
            .acknowledge(
                STOCK_RESTORE_STREAM,
                STOCK_RESTORE_GROUP,
                RecordId.of("1-0")
            )
    }

    @Test
    @DisplayName("Pending 메시지 재처리에 실패하면 ACK 처리하지 않는다")
    fun doNotAckWhenPendingRetryFails() {
        // given
        val record = createRecord(
            eventId = "event-123",
            traceId = "trace-123",
            payload = """
                {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
            """.trimIndent()
        )

        val pendingMessage =
            Mockito.mock(PendingMessage::class.java)

        Mockito.`when`(pendingMessage.id)
            .thenReturn(RecordId.of("1-0"))

        val pendingMessages =
            Mockito.mock(PendingMessages::class.java)

        Mockito.`when`(pendingMessages.isEmpty)
            .thenReturn(false)

        Mockito.`when`(pendingMessages.iterator())
            .thenReturn(
                mutableListOf(pendingMessage).iterator()
            )

        Mockito.`when`(
            stringRedisTemplate.opsForStream<Any, Any>()
        ).thenReturn(streamOperations)

        Mockito.`when`(
            streamOperations.pending(
                eq(STOCK_RESTORE_STREAM),
                eq(STOCK_RESTORE_GROUP),
                any<Range<String>>(),
                anyLong(),
                any(Duration::class.java)
            )
        ).thenReturn(pendingMessages)

        Mockito.`when`(
            streamOperations.claim(
                eq(STOCK_RESTORE_STREAM),
                eq(STOCK_RESTORE_GROUP),
                eq(STOCK_RESTORE_CONSUMER),
                any(Duration::class.java),
                eq(RecordId.of("1-0"))
            )
        ).thenReturn(
            listOf(recordAsAny(record))
        )

        Mockito.doThrow(
            RuntimeException("재처리 실패")
        ).`when`(inventoryService)
            .restoreStockIdempotently(
                "event-123",
                2L,
                3L,
                1
            )

        // when
        stockRestoreConsumer.retryPending()

        // then
        Mockito.verify(
            streamOperations,
            Mockito.never()
        ).acknowledge(
            anyString(),
            anyString(),
            any(RecordId::class.java)
        )
    }

    private fun createRecord(
        eventId: String,
        traceId: String,
        payload: String
    ): MapRecord<String, String, String> =
        StreamRecords.newRecord()
            .`in`(STOCK_RESTORE_STREAM)
            .ofMap(
                mapOf(
                    "eventId" to eventId,
                    "traceId" to traceId,
                    "eventType" to "STOCK_RESTORE_REQUESTED",
                    "payload" to payload
                )
            )
            .withId(
                RecordId.of("1-0")
            )

    @Suppress("UNCHECKED_CAST")
    private fun mockRead(
        record: MapRecord<String, String, String>
    ) {
        Mockito.`when`(
            stringRedisTemplate.opsForStream<Any, Any>()
        ).thenReturn(streamOperations)

        Mockito.`when`(
            streamOperations.read(
                Mockito.any(Consumer::class.java),
                Mockito.any(StreamReadOptions::class.java),
                Mockito.any<StreamOffset<String>>()
            )
        ).thenReturn(
            listOf(recordAsAny(record))
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun recordAsAny(
        record: MapRecord<String, String, String>
    ): MapRecord<String, Any, Any> =
        record as MapRecord<String, Any, Any>
}