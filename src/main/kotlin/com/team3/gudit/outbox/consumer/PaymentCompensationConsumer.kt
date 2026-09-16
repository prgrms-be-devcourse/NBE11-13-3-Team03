package com.team3.gudit.outbox.consumer

import com.team3.gudit.outbox.dto.PaymentCompensationEventPayload
import com.team3.gudit.outbox.metrics.RedisStreamMetrics
import com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_CONSUMER
import com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_GROUP
import com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_STREAM
import com.team3.gudit.payment.dto.TossPaymentResponse
import com.team3.gudit.payment.service.PaymentService
import com.team3.gudit.payment.service.PaymentTransactionService
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Component
class PaymentCompensationConsumer(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val paymentService: PaymentService,
    private val paymentTransactionService: PaymentTransactionService,
    private val redisStreamMetrics: RedisStreamMetrics
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    fun consume() {
        val streamOperations =
            stringRedisTemplate.opsForStream<Any, Any>()

        val records = streamOperations.read(
            Consumer.from(
                PAYMENT_COMPENSATION_GROUP,
                PAYMENT_COMPENSATION_CONSUMER
            ),
            StreamReadOptions.empty()
                .count(BATCH_SIZE.toLong())
                .block(Duration.ofSeconds(1)),
            StreamOffset.create(
                PAYMENT_COMPENSATION_STREAM,
                ReadOffset.lastConsumed()
            )
        )

        if (records.isNullOrEmpty()) {
            return
        }

        for (record in records) {
            processRecord(record)
        }
    }

    @Scheduled(fixedDelay = 5000)
    fun retryPending() {
        val streamOperations =
            stringRedisTemplate.opsForStream<Any, Any>()

        val pendingMessages = streamOperations.pending(
            PAYMENT_COMPENSATION_STREAM,
            PAYMENT_COMPENSATION_GROUP,
            Range.unbounded<String>(),
            PENDING_BATCH_SIZE.toLong(),
            PENDING_MIN_IDLE
        )

        if (pendingMessages.isEmpty()) {
            return
        }

        for (pendingMessage in pendingMessages) {
            val claimedRecords = streamOperations.claim(
                PAYMENT_COMPENSATION_STREAM,
                PAYMENT_COMPENSATION_GROUP,
                PAYMENT_COMPENSATION_CONSUMER,
                PENDING_MIN_IDLE,
                pendingMessage.id
            )

            if (claimedRecords.isEmpty()) {
                continue
            }

            for (record in claimedRecords) {
                redisStreamMetrics.recordPaymentCompensationRetry()
                processRecord(record)
            }
        }
    }

    private fun processRecord(
        record: MapRecord<String, Any, Any>
    ) {
        val values = record.value

        val traceId = getValue(values, "traceId")
        val payload = getValue(values, "payload")

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
                    PaymentCompensationEventPayload::class.java
                )

            try {
                compensate(eventPayload)
            } catch (e: RuntimeException) {
                redisStreamMetrics.recordPaymentCompensationFailure()
                throw e
            }

            redisStreamMetrics.recordPaymentCompensationSuccess()

            stringRedisTemplate.opsForStream<Any, Any>()
                .acknowledge(
                    PAYMENT_COMPENSATION_STREAM,
                    PAYMENT_COMPENSATION_GROUP,
                    record.id
                )

            processingResult = "success"

        } catch (e: RuntimeException) {
            redisStreamMetrics
                .recordPaymentCompensationProcessingFailure()

            log.warn(
                "Payment compensation processing failed. recordId={}",
                record.id,
                e
            )
        } finally {
            redisStreamMetrics.recordConsumerProcessingTime(
                sample,
                "payment_compensation",
                processingResult
            )

            MDC.remove("traceId")
        }
    }

    private fun compensate(
        payload: PaymentCompensationEventPayload
    ) {
        val actualPayment: TossPaymentResponse =
            paymentService.getPayment(
                payload.paymentKey
            )

        when (actualPayment.status()) {
            "DONE" -> {
                paymentService.cancelPayment(
                    payload.paymentKey
                )

                paymentTransactionService
                    .compensateApprovalFailure(
                        payload.paymentKey
                    )
            }

            "CANCELED" ->
                paymentTransactionService
                    .compensateApprovalFailure(
                        payload.paymentKey
                    )

            else -> throw IllegalStateException(
                "Unsupported Toss payment status for compensation. status=" +
                        actualPayment.status() +
                        ", paymentKey=" +
                        payload.paymentKey
            )
        }
    }

    private fun getValue(
        values: Map<Any, Any>,
        key: String
    ): String? =
        values[key]?.toString()

    companion object {
        private const val BATCH_SIZE = 10
        private val PENDING_MIN_IDLE: Duration =
            Duration.ofSeconds(30)
        private const val PENDING_BATCH_SIZE = 10
    }
}