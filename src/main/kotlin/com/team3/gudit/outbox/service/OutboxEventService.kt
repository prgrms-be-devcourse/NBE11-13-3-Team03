package com.team3.gudit.outbox.service

import com.team3.gudit.outbox.dto.PaymentCompensationEventPayload
import com.team3.gudit.outbox.dto.StockRestoreEventPayload
import com.team3.gudit.outbox.entity.OutboxEvent
import com.team3.gudit.outbox.entity.OutboxEventType
import com.team3.gudit.outbox.repository.OutboxEventRepository
import org.slf4j.MDC
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class OutboxEventService(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) {

    fun saveStockRestoreRequested(
        purchaseId: Long?,
        saleId: Long?,
        userId: Long?,
        quantity: Int
    ) {
        val payload = StockRestoreEventPayload(
            purchaseId = purchaseId,
            saleId = saleId,
            userId = userId,
            quantity = quantity
        )

        val event = OutboxEvent.create(
            traceId = MDC.get("traceId"),
            eventType = OutboxEventType.STOCK_RESTORE_REQUESTED,
            payload = serialize(payload)
        )

        outboxEventRepository.save(event)
    }

    fun savePaymentCompensationRequired(
        paymentId: Long?,
        orderId: String,
        paymentKey: String
    ) {
        val payload = PaymentCompensationEventPayload(
            paymentId = paymentId,
            orderId = orderId,
            paymentKey = paymentKey
        )

        val event = OutboxEvent.create(
            traceId = MDC.get("traceId"),
            eventType = OutboxEventType.PAYMENT_COMPENSATION_REQUIRED,
            payload = serialize(payload)
        )

        outboxEventRepository.save(event)
    }

    private fun serialize(payload: Any): String =
        objectMapper.writeValueAsString(payload)
}