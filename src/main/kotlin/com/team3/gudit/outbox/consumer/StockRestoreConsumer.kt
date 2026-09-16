package com.team3.gudit.outbox.consumer

import com.team3.gudit.outbox.dto.StockRestoreEventPayload
import com.team3.gudit.outbox.metrics.RedisStreamMetrics
import com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_CONSUMER
import com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_GROUP
import com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_STREAM
import com.team3.gudit.sale.service.InventoryService
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Component
class StockRestoreConsumer(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val inventoryService: InventoryService,
    private val redisStreamMetrics: RedisStreamMetrics
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    fun consume() {
        val streamOperations =
            stringRedisTemplate.opsForStream<Any, Any>()

        val records = streamOperations.read(
            Consumer.from(
                STOCK_RESTORE_GROUP,
                STOCK_RESTORE_CONSUMER
            ),
            StreamReadOptions.empty()
                .count(BATCH_SIZE.toLong())
                .block(Duration.ofSeconds(1)),
            StreamOffset.create(
                STOCK_RESTORE_STREAM,
                ReadOffset.lastConsumed()
            )
        )

        if (records.isNullOrEmpty()) {
            return
        }

        for (record in records) {
            processRecord(
                streamOperations,
                record
            )
        }
    }

    @Scheduled(fixedDelay = 5000)
    fun retryPending() {
        val streamOperations =
            stringRedisTemplate.opsForStream<Any, Any>()

        val pendingMessages = streamOperations.pending(
            STOCK_RESTORE_STREAM,
            STOCK_RESTORE_GROUP,
            Range.unbounded<String>(),
            PENDING_BATCH_SIZE,
            PENDING_MIN_IDLE
        )

        if (pendingMessages.isEmpty()) {
            return
        }

        for (pendingMessage in pendingMessages) {
            val claimedRecords = streamOperations.claim(
                STOCK_RESTORE_STREAM,
                STOCK_RESTORE_GROUP,
                STOCK_RESTORE_CONSUMER,
                PENDING_MIN_IDLE,
                pendingMessage.id
            )

            if (claimedRecords.isEmpty()) {
                continue
            }

            for (record in claimedRecords) {
                redisStreamMetrics.recordStockRestoreRetry()

                processRecord(
                    streamOperations,
                    record
                )
            }
        }
    }

    private fun processRecord(
        streamOperations: StreamOperations<String, Any, Any>,
        record: MapRecord<String, Any, Any>
    ) {
        val eventId = getValue(record, "eventId")
        val traceId = getValue(record, "traceId")
        val payload = getValue(record, "payload")

        val sample: Timer.Sample =
            redisStreamMetrics.startConsumerProcessingTimer()

        var processingResult = "failed"

        try {
            if (!traceId.isNullOrBlank()) {
                MDC.put("traceId", traceId)
            }

            val eventPayload =
                objectMapper.readValue(
                    payload,
                    StockRestoreEventPayload::class.java
                )

            inventoryService.restoreStockIdempotently(
                eventId,
                eventPayload.saleId,
                eventPayload.userId,
                eventPayload.quantity
            )

            streamOperations.acknowledge(
                STOCK_RESTORE_STREAM,
                STOCK_RESTORE_GROUP,
                record.id
            )

            processingResult = "success"

            log.info(
                "Redis Stream 재고 복구 처리 완료. eventId={}, purchaseId={}, saleId={}, userId={}, quantity={}",
                eventId,
                eventPayload.purchaseId,
                eventPayload.saleId,
                eventPayload.userId,
                eventPayload.quantity
            )
        } catch (e: RuntimeException) {
            redisStreamMetrics.recordStockRestoreProcessingFailure()

            log.warn(
                "Redis Stream 재고 복구 처리 실패. eventId={}, recordId={}",
                eventId,
                record.id,
                e
            )
        } finally {
            redisStreamMetrics.recordConsumerProcessingTime(
                sample,
                "stock_restore",
                processingResult
            )

            MDC.remove("traceId")
        }
    }

    private fun getValue(
        record: MapRecord<String, Any, Any>,
        key: String
    ): String? =
        record.value[key]?.toString()

    companion object {
        private val PENDING_MIN_IDLE: Duration =
            Duration.ofSeconds(30)

        private const val PENDING_BATCH_SIZE = 10L
        private const val BATCH_SIZE = 10
    }
}