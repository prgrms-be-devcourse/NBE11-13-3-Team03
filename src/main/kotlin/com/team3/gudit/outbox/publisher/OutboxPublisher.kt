package com.team3.gudit.outbox.publisher

import com.team3.gudit.outbox.entity.OutboxEvent
import com.team3.gudit.outbox.entity.OutboxEventStatus
import com.team3.gudit.outbox.entity.OutboxEventType
import com.team3.gudit.outbox.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val stringRedisTemplate: StringRedisTemplate
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    @Transactional
    fun publishPendingEvents() {
        val events =
            outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING
            )

        for (event in events) {
            publish(event)
        }
    }

    private fun publish(event: OutboxEvent) {
        try {
            val record: MapRecord<String, String, String> =
                StreamRecords
                    .newRecord()
                    .`in`(resolveStream(event))
                    .ofMap(
                        mapOf(
                            "eventId" to event.eventId,
                            "traceId" to (event.traceId ?: ""),
                            "eventType" to event.eventType.name,
                            "payload" to event.payload
                        )
                    )

            stringRedisTemplate
                .opsForStream<String, String>()
                .add(record)

            event.markPublished()

            log.info(
                "Outbox event published. eventId={}, eventType={}",
                event.eventId,
                event.eventType
            )
        } catch (e: RuntimeException) {
            log.warn(
                "Outbox event publish failed. eventId={}, eventType={}",
                event.eventId,
                event.eventType,
                e
            )
        }
    }

    private fun resolveStream(event: OutboxEvent): String =
        when (event.eventType) {
            OutboxEventType.STOCK_RESTORE_REQUESTED ->
                StockRestoreStreamConstants.STOCK_RESTORE_STREAM

            OutboxEventType.PAYMENT_COMPENSATION_REQUIRED ->
                PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_STREAM
        }
}