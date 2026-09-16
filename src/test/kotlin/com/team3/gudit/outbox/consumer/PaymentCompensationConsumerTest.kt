package com.team3.gudit.outbox.consumer

import com.team3.gudit.outbox.metrics.RedisStreamMetrics
import com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_CONSUMER
import com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_GROUP
import com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_STREAM
import com.team3.gudit.payment.dto.TossPaymentResponse
import com.team3.gudit.payment.service.PaymentService
import com.team3.gudit.payment.service.PaymentTransactionService
import io.micrometer.core.instrument.Timer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.PendingMessage
import org.springframework.data.redis.connection.stream.PendingMessages
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate
import tools.jackson.databind.ObjectMapper
import java.time.OffsetDateTime

@ExtendWith(MockitoExtension::class)
class PaymentCompensationConsumerTest {

    @Mock
    lateinit var stringRedisTemplate: StringRedisTemplate

    @Mock
    lateinit var streamOperations: StreamOperations<String, Any, Any>

    @Mock
    lateinit var paymentService: PaymentService

    @Mock
    lateinit var paymentTransactionService: PaymentTransactionService

    @Mock
    lateinit var redisStreamMetrics: RedisStreamMetrics

    @Mock
    lateinit var timerSample: Timer.Sample

    private lateinit var objectMapper: ObjectMapper
    private lateinit var consumer: PaymentCompensationConsumer

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper()

        consumer = PaymentCompensationConsumer(
            stringRedisTemplate,
            objectMapper,
            paymentService,
            paymentTransactionService,
            redisStreamMetrics
        )

        Mockito.`when`(
            stringRedisTemplate.opsForStream<Any, Any>()
        ).thenReturn(streamOperations)

        Mockito.`when`(
            redisStreamMetrics.startConsumerProcessingTimer()
        ).thenReturn(timerSample)
    }

    @Test
    fun DONE_상태면_Toss_취소후_내부_보상을_처리하고_ACK한다() {
        // given
        val paymentKey = "payment-key-1"

        val record = createRecord(
            """
            {
              "paymentId":1,
              "orderId":"order-1",
              "paymentKey":"payment-key-1"
            }
            """
        )

        val response = TossPaymentResponse(
            paymentKey,
            "order-1",
            "DONE",
            10000,
            OffsetDateTime.now()
        )

        mockRead(record)

        Mockito.`when`(
            paymentService.getPayment(paymentKey)
        ).thenReturn(response)

        // when
        consumer.consume()

        // then
        Mockito.verify(paymentService)
            .getPayment(paymentKey)

        Mockito.verify(paymentService)
            .cancelPayment(paymentKey)

        Mockito.verify(paymentTransactionService)
            .compensateApprovalFailure(paymentKey)

        Mockito.verify(streamOperations)
            .acknowledge(
                PAYMENT_COMPENSATION_STREAM,
                PAYMENT_COMPENSATION_GROUP,
                record.id
            )
    }

    @Test
    fun CANCELED_상태면_Toss_취소없이_내부_보상만_처리하고_ACK한다() {
        // given
        val paymentKey = "payment-key-1"

        val record = createRecord(
            """
            {
              "paymentId":1,
              "orderId":"order-1",
              "paymentKey":"payment-key-1"
            }
            """
        )

        val response = TossPaymentResponse(
            paymentKey,
            "order-1",
            "CANCELED",
            10000,
            OffsetDateTime.now()
        )

        mockRead(record)

        Mockito.`when`(
            paymentService.getPayment(paymentKey)
        ).thenReturn(response)

        // when
        consumer.consume()

        // then
        Mockito.verify(paymentService)
            .getPayment(paymentKey)

        Mockito.verify(
            paymentService,
            Mockito.never()
        ).cancelPayment(
            Mockito.anyString()
        )

        Mockito.verify(paymentTransactionService)
            .compensateApprovalFailure(paymentKey)

        Mockito.verify(streamOperations)
            .acknowledge(
                PAYMENT_COMPENSATION_STREAM,
                PAYMENT_COMPENSATION_GROUP,
                record.id
            )
    }

    @Test
    fun Toss_조회가_실패하면_ACK하지_않는다() {
        // given
        val paymentKey = "payment-key-1"

        val record = createRecord(
            """
            {
              "paymentId":1,
              "orderId":"order-1",
              "paymentKey":"payment-key-1"
            }
            """
        )

        mockRead(record)

        Mockito.`when`(
            paymentService.getPayment(paymentKey)
        ).thenThrow(
            RuntimeException("Toss 조회 실패")
        )

        // when
        consumer.consume()

        // then
        Mockito.verify(paymentService)
            .getPayment(paymentKey)

        Mockito.verify(
            paymentService,
            Mockito.never()
        ).cancelPayment(
            Mockito.anyString()
        )

        Mockito.verify(
            paymentTransactionService,
            Mockito.never()
        ).compensateApprovalFailure(
            Mockito.anyString()
        )

        Mockito.verify(
            streamOperations,
            Mockito.never()
        ).acknowledge(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(RecordId::class.java)
        )
    }

    @Test
    fun Toss_취소가_실패하면_ACK하지_않는다() {
        // given
        val paymentKey = "payment-key-1"

        val record = createRecord(
            """
            {
              "paymentId":1,
              "orderId":"order-1",
              "paymentKey":"payment-key-1"
            }
            """
        )

        val response = TossPaymentResponse(
            paymentKey,
            "order-1",
            "DONE",
            10000,
            OffsetDateTime.now()
        )

        mockRead(record)

        Mockito.`when`(
            paymentService.getPayment(paymentKey)
        ).thenReturn(response)

        Mockito.`when`(
            paymentService.cancelPayment(paymentKey)
        ).thenThrow(
            RuntimeException("Toss 취소 실패")
        )

        // when
        consumer.consume()

        // then
        Mockito.verify(paymentService)
            .cancelPayment(paymentKey)

        Mockito.verify(
            paymentTransactionService,
            Mockito.never()
        ).compensateApprovalFailure(
            Mockito.anyString()
        )

        Mockito.verify(
            streamOperations,
            Mockito.never()
        ).acknowledge(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(RecordId::class.java)
        )
    }

    @Test
    fun 내부_보상_처리가_실패하면_ACK하지_않는다() {
        // given
        val paymentKey = "payment-key-1"

        val record = createRecord(
            """
            {
              "paymentId":1,
              "orderId":"order-1",
              "paymentKey":"payment-key-1"
            }
            """
        )

        val response = TossPaymentResponse(
            paymentKey,
            "order-1",
            "CANCELED",
            10000,
            OffsetDateTime.now()
        )

        mockRead(record)

        Mockito.`when`(
            paymentService.getPayment(paymentKey)
        ).thenReturn(response)

        Mockito.doThrow(
            RuntimeException("내부 보상 실패")
        ).`when`(paymentTransactionService)
            .compensateApprovalFailure(paymentKey)

        // when
        consumer.consume()

        // then
        Mockito.verify(paymentTransactionService)
            .compensateApprovalFailure(paymentKey)

        Mockito.verify(
            streamOperations,
            Mockito.never()
        ).acknowledge(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.any(RecordId::class.java)
        )
    }

    @Test
    fun Pending_메시지를_claim한뒤_재처리에_성공하면_ACK한다() {
        // given
        val paymentKey = "payment-key-1"

        val record = createRecord(
            """
            {
              "paymentId":1,
              "orderId":"order-1",
              "paymentKey":"payment-key-1"
            }
            """
        )

        val response = TossPaymentResponse(
            paymentKey,
            "order-1",
            "CANCELED",
            10000,
            OffsetDateTime.now()
        )

        val pendingMessages =
            Mockito.mock(PendingMessages::class.java)

        val pendingMessage =
            Mockito.mock(PendingMessage::class.java)

        Mockito.`when`(
            pendingMessage.id
        ).thenReturn(record.id)

        Mockito.`when`(
            pendingMessages.iterator()
        ).thenReturn(
            mutableListOf(pendingMessage).iterator()
        )

        Mockito.`when`(
            streamOperations.pending(
                Mockito.eq(PAYMENT_COMPENSATION_STREAM),
                Mockito.eq(PAYMENT_COMPENSATION_GROUP),
                Mockito.any(),
                Mockito.anyLong(),
                Mockito.any()
            )
        ).thenReturn(pendingMessages)

        Mockito.`when`(
            streamOperations.claim(
                Mockito.eq(PAYMENT_COMPENSATION_STREAM),
                Mockito.eq(PAYMENT_COMPENSATION_GROUP),
                Mockito.eq(PAYMENT_COMPENSATION_CONSUMER),
                Mockito.any(),
                Mockito.eq(record.id)
            )
        ).thenReturn(
            listOf(record)
        )

        Mockito.`when`(
            paymentService.getPayment(paymentKey)
        ).thenReturn(response)

        // when
        consumer.retryPending()

        // then
        Mockito.verify(streamOperations)
            .claim(
                Mockito.eq(PAYMENT_COMPENSATION_STREAM),
                Mockito.eq(PAYMENT_COMPENSATION_GROUP),
                Mockito.eq(PAYMENT_COMPENSATION_CONSUMER),
                Mockito.any(),
                Mockito.eq(record.id)
            )

        Mockito.verify(paymentTransactionService)
            .compensateApprovalFailure(paymentKey)

        Mockito.verify(streamOperations)
            .acknowledge(
                PAYMENT_COMPENSATION_STREAM,
                PAYMENT_COMPENSATION_GROUP,
                record.id
            )
    }

    @Test
    fun 결제_보상은_성공했지만_ACK가_실패하면_결제보상실패로_집계하지_않는다() {
        // given
        val paymentKey = "payment-key-1"

        val record = createRecord(
            """
            {
              "paymentId":1,
              "orderId":"order-1",
              "paymentKey":"payment-key-1"
            }
            """
        )

        val response = TossPaymentResponse(
            paymentKey,
            "order-1",
            "CANCELED",
            10000,
            OffsetDateTime.now()
        )

        mockRead(record)

        Mockito.`when`(
            paymentService.getPayment(paymentKey)
        ).thenReturn(response)

        Mockito.doThrow(
            RuntimeException("Redis ACK 실패")
        ).`when`(streamOperations)
            .acknowledge(
                PAYMENT_COMPENSATION_STREAM,
                PAYMENT_COMPENSATION_GROUP,
                record.id
            )

        // when
        consumer.consume()

        // then
        Mockito.verify(paymentTransactionService)
            .compensateApprovalFailure(paymentKey)

        Mockito.verify(redisStreamMetrics)
            .recordPaymentCompensationSuccess()

        Mockito.verify(
            redisStreamMetrics,
            Mockito.never()
        ).recordPaymentCompensationFailure()

        Mockito.verify(redisStreamMetrics)
            .recordPaymentCompensationProcessingFailure()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mockRead(
        record: MapRecord<String, Any, Any>
    ) {
        Mockito.`when`(
            streamOperations.read(
                Mockito.any(Consumer::class.java),
                Mockito.any(StreamReadOptions::class.java),
                Mockito.any<StreamOffset<String>>()
            )
        ).thenReturn(
            listOf(record)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun createRecord(
        payload: String
    ): MapRecord<String, Any, Any> =
        StreamRecords
            .newRecord()
            .`in`(PAYMENT_COMPENSATION_STREAM)
            .ofMap(
                mapOf(
                    "eventId" to "event-123",
                    "traceId" to "trace-123",
                    "eventType" to
                            "PAYMENT_COMPENSATION_REQUIRED",
                    "payload" to payload
                )
            ) as MapRecord<String, Any, Any>
}