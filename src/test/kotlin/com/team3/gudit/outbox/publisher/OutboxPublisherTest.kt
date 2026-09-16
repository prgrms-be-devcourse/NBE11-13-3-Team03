package com.team3.gudit.outbox.publisher

import com.team3.gudit.outbox.entity.OutboxEvent
import com.team3.gudit.outbox.entity.OutboxEventStatus
import com.team3.gudit.outbox.entity.OutboxEventType
import com.team3.gudit.outbox.repository.OutboxEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate

@ExtendWith(MockitoExtension::class)
class OutboxPublisherTest {

    @Mock
    lateinit var outboxEventRepository: OutboxEventRepository

    @Mock
    lateinit var stringRedisTemplate: StringRedisTemplate

    @Mock
    lateinit var streamOperations: StreamOperations<String, Any, Any>

    private lateinit var outboxPublisher: OutboxPublisher

    @BeforeEach
    fun setUp() {
        outboxPublisher = OutboxPublisher(
            outboxEventRepository,
            stringRedisTemplate
        )
    }

    @Test
    @DisplayName("PENDING Outbox 이벤트를 Redis Stream에 발행하고 PUBLISHED 상태로 변경한다")
    fun publishPendingEvent() {
        // given
        val payload = """
            {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
            """

        val event = OutboxEvent.create(
            "trace-123",
            OutboxEventType.STOCK_RESTORE_REQUESTED,
            payload
        )

        Mockito.`when`(
            outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING
            )
        ).thenReturn(listOf(event))

        Mockito.`when`(
            stringRedisTemplate.opsForStream<Any, Any>()
        ).thenReturn(streamOperations)

        Mockito.`when`(
            streamOperations.add(Mockito.any())
        ).thenReturn(RecordId.of("1-0"))

        @Suppress("UNCHECKED_CAST")
        val captor =
            ArgumentCaptor.forClass(MapRecord::class.java)
                    as ArgumentCaptor<MapRecord<String, out Any, out Any>>

        // when
        outboxPublisher.publishPendingEvents()

        // then
        Mockito.verify(streamOperations)
            .add(captor.capture())

        val record = captor.value

        assertThat(record.stream)
            .isEqualTo(
                StockRestoreStreamConstants.STOCK_RESTORE_STREAM
            )

        val values = record.value

        assertThat(values["eventId"])
            .isEqualTo(event.eventId)

        assertThat(values["traceId"])
            .isEqualTo("trace-123")

        assertThat(values["eventType"])
            .isEqualTo(
                OutboxEventType.STOCK_RESTORE_REQUESTED.name
            )

        assertThat(values["payload"])
            .isEqualTo(payload)

        assertThat(event.status)
            .isEqualTo(OutboxEventStatus.PUBLISHED)
    }

    @Test
    @DisplayName("Redis Stream 발행에 실패하면 Outbox 이벤트를 PENDING 상태로 유지한다")
    fun keepPendingWhenPublishFails() {
        // given
        val event = OutboxEvent.create(
            "trace-123",
            OutboxEventType.STOCK_RESTORE_REQUESTED,
            """
            {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
            """
        )

        Mockito.`when`(
            outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING
            )
        ).thenReturn(listOf(event))

        Mockito.`when`(
            stringRedisTemplate.opsForStream<Any, Any>()
        ).thenReturn(streamOperations)

        Mockito.`when`(
            streamOperations.add(Mockito.any())
        ).thenThrow(
            RuntimeException("Redis connection failed")
        )

        // when
        outboxPublisher.publishPendingEvents()

        // then
        assertThat(event.status)
            .isEqualTo(OutboxEventStatus.PENDING)
    }

    @Test
    @DisplayName("PENDING Outbox 이벤트가 없으면 Redis Stream에 발행하지 않는다")
    fun doNothingWhenNoPendingEvents() {
        // given
        Mockito.`when`(
            outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING
            )
        ).thenReturn(emptyList())

        // when
        outboxPublisher.publishPendingEvents()

        // then
        Mockito.verify(
            stringRedisTemplate,
            Mockito.never()
        ).opsForStream<Any, Any>()
    }

    @Test
    @DisplayName("Redis Stream 발행 실패 후 다음 실행에서 재발행에 성공하면 PUBLISHED로 변경한다")
    fun retryPublishAfterFailure() {
        // given
        val event = OutboxEvent.create(
            "trace-123",
            OutboxEventType.STOCK_RESTORE_REQUESTED,
            """
            {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
            """
        )

        Mockito.`when`(
            outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING
            )
        ).thenReturn(
            listOf(event),
            listOf(event)
        )

        Mockito.`when`(
            stringRedisTemplate.opsForStream<Any, Any>()
        ).thenReturn(streamOperations)

        Mockito.`when`(
            streamOperations.add(Mockito.any())
        )
            .thenThrow(
                RuntimeException("Redis connection failed")
            )
            .thenReturn(
                RecordId.of("1-0")
            )

        // when - 첫 번째 발행 실패
        outboxPublisher.publishPendingEvents()

        // then
        assertThat(event.status)
            .isEqualTo(OutboxEventStatus.PENDING)

        // when - 다음 스케줄에서 재발행
        outboxPublisher.publishPendingEvents()

        // then
        assertThat(event.status)
            .isEqualTo(OutboxEventStatus.PUBLISHED)

        Mockito.verify(
            streamOperations,
            Mockito.times(2)
        ).add(Mockito.any())
    }

    @Test
    @DisplayName("PAYMENT_COMPENSATION_REQUIRED 이벤트는 결제 보상 Stream으로 발행한다")
    fun publishPaymentCompensationEvent() {
        // given
        val event = OutboxEvent.create(
            "trace-123",
            OutboxEventType.PAYMENT_COMPENSATION_REQUIRED,
            """
            {
              "paymentId":1,
              "orderId":"order-1",
              "paymentKey":"payment-key-1"
            }
            """
        )

        Mockito.`when`(
            outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING
            )
        ).thenReturn(listOf(event))

        Mockito.`when`(
            stringRedisTemplate.opsForStream<Any, Any>()
        ).thenReturn(streamOperations)

        Mockito.`when`(
            streamOperations.add(Mockito.any())
        ).thenReturn(
            RecordId.of("1-0")
        )

        @Suppress("UNCHECKED_CAST")
        val captor =
            ArgumentCaptor.forClass(MapRecord::class.java)
                    as ArgumentCaptor<MapRecord<String, out Any, out Any>>

        // when
        outboxPublisher.publishPendingEvents()

        // then
        Mockito.verify(streamOperations)
            .add(captor.capture())

        val publishedRecord = captor.value

        assertThat(publishedRecord.stream)
            .isEqualTo(
                PaymentCompensationStreamConstants
                    .PAYMENT_COMPENSATION_STREAM
            )

        assertThat(
            publishedRecord.value["eventType"]
        ).isEqualTo(
            "PAYMENT_COMPENSATION_REQUIRED"
        )

        assertThat(
            publishedRecord.value["traceId"]
        ).isEqualTo(
            "trace-123"
        )

        assertThat(event.status)
            .isEqualTo(OutboxEventStatus.PUBLISHED)
    }
}