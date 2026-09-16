package com.team3.gudit.outbox.service

import com.team3.gudit.outbox.dto.PaymentCompensationEventPayload
import com.team3.gudit.outbox.entity.OutboxEvent
import com.team3.gudit.outbox.entity.OutboxEventStatus
import com.team3.gudit.outbox.entity.OutboxEventType
import com.team3.gudit.outbox.repository.OutboxEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.slf4j.MDC
import tools.jackson.databind.ObjectMapper

@ExtendWith(MockitoExtension::class)
class OutboxEventServiceTest {

    @Mock
    lateinit var outboxEventRepository: OutboxEventRepository

    private lateinit var objectMapper: ObjectMapper
    private lateinit var outboxEventService: OutboxEventService

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper()

        outboxEventService = OutboxEventService(
            outboxEventRepository,
            objectMapper
        )
    }

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    @DisplayName("재고 복구 요청을 PENDING 상태의 Outbox 이벤트로 저장한다")
    fun saveStockRestoreRequested() {
        // given
        val purchaseId = 1L
        val saleId = 2L
        val userId = 3L
        val quantity = 1

        MDC.put("traceId", "trace-123")

        // when
        outboxEventService.saveStockRestoreRequested(
            purchaseId,
            saleId,
            userId,
            quantity
        )

        // then
        val captor = ArgumentCaptor.forClass(OutboxEvent::class.java)

        verify(outboxEventRepository).save(captor.capture())

        val savedEvent = captor.value

        assertThat(savedEvent.eventId).isNotBlank()
        assertThat(savedEvent.traceId).isEqualTo("trace-123")
        assertThat(savedEvent.eventType)
            .isEqualTo(OutboxEventType.STOCK_RESTORE_REQUESTED)
        assertThat(savedEvent.status)
            .isEqualTo(OutboxEventStatus.PENDING)

        assertThat(savedEvent.payload)
            .contains("\"purchaseId\":1")
            .contains("\"saleId\":2")
            .contains("\"userId\":3")
            .contains("\"quantity\":1")
    }

    @Test
    @DisplayName("traceId가 없어도 재고 복구 Outbox 이벤트를 저장한다")
    fun saveStockRestoreRequestedWithoutTraceId() {
        // given
        val purchaseId = 1L
        val saleId = 2L
        val userId = 3L
        val quantity = 1

        // when
        outboxEventService.saveStockRestoreRequested(
            purchaseId,
            saleId,
            userId,
            quantity
        )

        // then
        val captor = ArgumentCaptor.forClass(OutboxEvent::class.java)

        verify(outboxEventRepository).save(captor.capture())

        val savedEvent = captor.value

        assertThat(savedEvent.traceId).isNull()
        assertThat(savedEvent.eventType)
            .isEqualTo(OutboxEventType.STOCK_RESTORE_REQUESTED)
        assertThat(savedEvent.status)
            .isEqualTo(OutboxEventStatus.PENDING)
    }

    @Test
    @DisplayName("결제 보상 재처리 요청을 PAYMENT_COMPENSATION_REQUIRED Outbox 이벤트로 저장한다")
    fun savePaymentCompensationRequired() {
        // given
        val paymentId = 1L
        val orderId = "order-1"
        val paymentKey = "payment-key-1"

        // when
        outboxEventService.savePaymentCompensationRequired(
            paymentId,
            orderId,
            paymentKey
        )

        // then
        val captor = ArgumentCaptor.forClass(OutboxEvent::class.java)

        verify(outboxEventRepository).save(captor.capture())

        val savedEvent = captor.value

        assertThat(savedEvent.eventType)
            .isEqualTo(
                OutboxEventType.PAYMENT_COMPENSATION_REQUIRED
            )

        assertThat(savedEvent.status)
            .isEqualTo(OutboxEventStatus.PENDING)

        assertThat(savedEvent.eventId)
            .isNotNull()

        val payload = objectMapper.readValue(
            savedEvent.payload,
            PaymentCompensationEventPayload::class.java
        )

        assertThat(payload.paymentId)
            .isEqualTo(paymentId)

        assertThat(payload.orderId)
            .isEqualTo(orderId)

        assertThat(payload.paymentKey)
            .isEqualTo(paymentKey)
    }
}