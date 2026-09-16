package com.team3.gudit.outbox.metrics

import com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_GROUP
import com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_STREAM
import com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_GROUP
import com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_STREAM
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class RedisStreamMetrics(
    private val redisTemplate: StringRedisTemplate,
    private val meterRegistry: MeterRegistry
) {

    private val stockRestorePendingCount = AtomicLong()
    private val paymentCompensationPendingCount = AtomicLong()

    private val stockRestoreRetryCounter: Counter
    private val paymentCompensationRetryCounter: Counter
    private val stockRestoreProcessingFailureCounter: Counter
    private val paymentCompensationProcessingFailureCounter: Counter
    private val paymentCompensationSuccessCounter: Counter
    private val paymentCompensationFailureCounter: Counter

    private val stockRestoreConsumerLag = AtomicLong()
    private val paymentCompensationConsumerLag = AtomicLong()

    private val stockRestoreMaxPendingIdleSeconds = AtomicLong()
    private val paymentCompensationMaxPendingIdleSeconds = AtomicLong()

    init {
        registerPendingGauge(
            meterRegistry,
            stockRestorePendingCount,
            "stock_restore"
        )

        registerPendingGauge(
            meterRegistry,
            paymentCompensationPendingCount,
            "payment_compensation"
        )

        stockRestoreRetryCounter = Counter.builder(
            "gudit.redis.stream.retry"
        )
            .description("Number of Redis Stream message retries")
            .tag("stream", "stock_restore")
            .register(meterRegistry)

        paymentCompensationRetryCounter = Counter.builder(
            "gudit.redis.stream.retry"
        )
            .description("Number of Redis Stream message retries")
            .tag("stream", "payment_compensation")
            .register(meterRegistry)

        stockRestoreProcessingFailureCounter = Counter.builder(
            "gudit.redis.stream.consumer.processing.failure"
        )
            .description("Number of Redis Stream consumer processing failures")
            .tag("stream", "stock_restore")
            .register(meterRegistry)

        paymentCompensationProcessingFailureCounter = Counter.builder(
            "gudit.redis.stream.consumer.processing.failure"
        )
            .description("Number of Redis Stream consumer processing failures")
            .tag("stream", "payment_compensation")
            .register(meterRegistry)

        paymentCompensationSuccessCounter = Counter.builder(
            "gudit.payment.compensation"
        )
            .description("Number of payment compensation results")
            .tag("result", "success")
            .register(meterRegistry)

        paymentCompensationFailureCounter = Counter.builder(
            "gudit.payment.compensation"
        )
            .description("Number of payment compensation results")
            .tag("result", "failed")
            .register(meterRegistry)

        registerProcessingTimer(
            "stock_restore",
            "success"
        )

        registerProcessingTimer(
            "stock_restore",
            "failed"
        )

        registerProcessingTimer(
            "payment_compensation",
            "success"
        )

        registerProcessingTimer(
            "payment_compensation",
            "failed"
        )

        registerLagGauge(
            meterRegistry,
            stockRestoreConsumerLag,
            "stock_restore"
        )

        registerLagGauge(
            meterRegistry,
            paymentCompensationConsumerLag,
            "payment_compensation"
        )

        registerPendingMaxIdleGauge(
            meterRegistry,
            stockRestoreMaxPendingIdleSeconds,
            "stock_restore"
        )

        registerPendingMaxIdleGauge(
            meterRegistry,
            paymentCompensationMaxPendingIdleSeconds,
            "payment_compensation"
        )
    }

    private fun registerPendingGauge(
        meterRegistry: MeterRegistry,
        value: AtomicLong,
        stream: String
    ) {
        Gauge.builder(
            "gudit.redis.stream.pending.count",
            value
        ) { it.get().toDouble() }
            .description(
                "Number of delivered but unacknowledged Redis Stream messages"
            )
            .tag("stream", stream)
            .register(meterRegistry)
    }

    @Scheduled(
        initialDelay = 5000,
        fixedDelay = 5000
    )
    fun refreshPendingCounts() {
        stockRestorePendingCount.set(
            getPendingCount(
                STOCK_RESTORE_STREAM,
                STOCK_RESTORE_GROUP
            )
        )

        paymentCompensationPendingCount.set(
            getPendingCount(
                PAYMENT_COMPENSATION_STREAM,
                PAYMENT_COMPENSATION_GROUP
            )
        )

        stockRestoreConsumerLag.set(
            getConsumerLag(
                STOCK_RESTORE_STREAM,
                STOCK_RESTORE_GROUP
            )
        )

        paymentCompensationConsumerLag.set(
            getConsumerLag(
                PAYMENT_COMPENSATION_STREAM,
                PAYMENT_COMPENSATION_GROUP
            )
        )

        stockRestoreMaxPendingIdleSeconds.set(
            getMaxPendingIdleSeconds(
                STOCK_RESTORE_STREAM,
                STOCK_RESTORE_GROUP
            )
        )

        paymentCompensationMaxPendingIdleSeconds.set(
            getMaxPendingIdleSeconds(
                PAYMENT_COMPENSATION_STREAM,
                PAYMENT_COMPENSATION_GROUP
            )
        )
    }

    private fun getPendingCount(
        stream: String,
        group: String
    ): Long {
        val summary = redisTemplate.opsForStream<String, String>()
            .pending(stream, group)

        return summary?.totalPendingMessages ?: 0L
    }

    fun recordStockRestoreRetry() {
        stockRestoreRetryCounter.increment()
    }

    fun recordPaymentCompensationRetry() {
        paymentCompensationRetryCounter.increment()
    }

    fun recordStockRestoreProcessingFailure() {
        stockRestoreProcessingFailureCounter.increment()
    }

    fun recordPaymentCompensationProcessingFailure() {
        paymentCompensationProcessingFailureCounter.increment()
    }

    fun recordPaymentCompensationSuccess() {
        paymentCompensationSuccessCounter.increment()
    }

    fun recordPaymentCompensationFailure() {
        paymentCompensationFailureCounter.increment()
    }

    fun startConsumerProcessingTimer(): Timer.Sample =
        Timer.start(meterRegistry)

    fun recordConsumerProcessingTime(
        sample: Timer.Sample,
        stream: String,
        result: String
    ) {
        sample.stop(
            registerProcessingTimer(
                stream,
                result
            )
        )
    }

    private fun registerProcessingTimer(
        stream: String,
        result: String
    ): Timer =
        Timer.builder(
            "gudit.redis.stream.consumer.processing"
        )
            .description(
                "Redis Stream consumer processing time"
            )
            .tag("stream", stream)
            .tag("result", result)
            .publishPercentileHistogram()
            .register(meterRegistry)

    private fun registerLagGauge(
        meterRegistry: MeterRegistry,
        value: AtomicLong,
        stream: String
    ) {
        Gauge.builder(
            "gudit.redis.stream.consumer.lag",
            value
        ) { it.get().toDouble() }
            .description(
                "Number of Redis Stream messages not yet delivered to the consumer group"
            )
            .tag("stream", stream)
            .register(meterRegistry)
    }

    private fun getConsumerLag(
        stream: String,
        groupName: String
    ): Long {
        val groups = redisTemplate.opsForStream<String, String>()
            .groups(stream)
            ?: return 0L

        return groups
            .firstOrNull { it.groupName() == groupName }
            ?.let { parseLag(it.raw["lag"]) }
            ?: 0L
    }

    private fun parseLag(lag: Any?): Long =
        when (lag) {
            null -> 0L
            is Number -> lag.toLong()
            else -> lag.toString().toLong()
        }

    private fun registerPendingMaxIdleGauge(
        meterRegistry: MeterRegistry,
        value: AtomicLong,
        stream: String
    ) {
        Gauge.builder(
            "gudit.redis.stream.pending.max.idle.seconds",
            value
        ) { it.get().toDouble() }
            .description(
                "Maximum idle time of pending Redis Stream messages"
            )
            .tag("stream", stream)
            .register(meterRegistry)
    }

    private fun getMaxPendingIdleSeconds(
        stream: String,
        group: String
    ): Long {
        val pendingMessages = redisTemplate.opsForStream<String, String>()
            .pending(
                stream,
                group,
                Range.unbounded<String>(),
                PENDING_INSPECTION_LIMIT
            )

        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return 0L
        }

        var maxIdleSeconds = 0L

        for (pendingMessage in pendingMessages) {
            val idleSeconds = pendingMessage
                .elapsedTimeSinceLastDelivery
                .seconds

            maxIdleSeconds = maxOf(
                maxIdleSeconds,
                idleSeconds
            )
        }

        return maxIdleSeconds
    }

    companion object {
        private const val PENDING_INSPECTION_LIMIT = 1000L
    }
}