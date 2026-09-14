package com.team3.gudit.outbox.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

import static com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_GROUP;
import static com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.PAYMENT_COMPENSATION_STREAM;
import static com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_GROUP;
import static com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_STREAM;

@Component
public class RedisStreamMetrics {

    private final StringRedisTemplate redisTemplate;

    private final AtomicLong stockRestorePendingCount =
            new AtomicLong();

    private final AtomicLong paymentCompensationPendingCount = new AtomicLong();
    private final Counter stockRestoreRetryCounter;
    private final Counter paymentCompensationRetryCounter;
    private final Counter stockRestoreProcessingFailureCounter;
    private final Counter paymentCompensationProcessingFailureCounter;
    private final Counter paymentCompensationSuccessCounter;
    private final Counter paymentCompensationFailureCounter;
    private final MeterRegistry meterRegistry;
    private final AtomicLong stockRestoreConsumerLag = new AtomicLong();
    private final AtomicLong paymentCompensationConsumerLag = new AtomicLong();

    public RedisStreamMetrics(
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;

        registerPendingGauge(
                meterRegistry,
                stockRestorePendingCount,
                "stock_restore"
        );

        registerPendingGauge(
                meterRegistry,
                paymentCompensationPendingCount,
                "payment_compensation"
        );

        this.stockRestoreRetryCounter = Counter.builder(
                        "gudit.redis.stream.retry"
                )
                .description("Number of Redis Stream message retries")
                .tag("stream", "stock_restore")
                .register(meterRegistry);

        this.paymentCompensationRetryCounter = Counter.builder(
                        "gudit.redis.stream.retry"
                )
                .description("Number of Redis Stream message retries")
                .tag("stream", "payment_compensation")
                .register(meterRegistry);

        this.stockRestoreProcessingFailureCounter = Counter.builder(
                        "gudit.redis.stream.consumer.processing.failure"
                )
                .description("Number of Redis Stream consumer processing failures")
                .tag("stream", "stock_restore")
                .register(meterRegistry);

        this.paymentCompensationProcessingFailureCounter = Counter.builder(
                        "gudit.redis.stream.consumer.processing.failure"
                )
                .description("Number of Redis Stream consumer processing failures")
                .tag("stream", "payment_compensation")
                .register(meterRegistry);

        this.paymentCompensationSuccessCounter = Counter.builder(
                        "gudit.payment.compensation"
                )
                .description("Number of payment compensation results")
                .tag("result", "success")
                .register(meterRegistry);

        this.paymentCompensationFailureCounter = Counter.builder(
                        "gudit.payment.compensation"
                )
                .description("Number of payment compensation results")
                .tag("result", "failed")
                .register(meterRegistry);

        registerProcessingTimer(
                "stock_restore",
                "success"
        );

        registerProcessingTimer(
                "stock_restore",
                "failed"
        );

        registerProcessingTimer(
                "payment_compensation",
                "success"
        );

        registerProcessingTimer(
                "payment_compensation",
                "failed"
        );

        registerLagGauge(
                meterRegistry,
                stockRestoreConsumerLag,
                "stock_restore"
        );

        registerLagGauge(
                meterRegistry,
                paymentCompensationConsumerLag,
                "payment_compensation"
        );
    }

    private void registerPendingGauge(
            MeterRegistry meterRegistry,
            AtomicLong value,
            String stream
    ) {
        Gauge.builder(
                        "gudit.redis.stream.pending.count",
                        value,
                        AtomicLong::get
                )
                .description(
                        "Number of delivered but unacknowledged Redis Stream messages"
                )
                .tag("stream", stream)
                .register(meterRegistry);
    }

    @Scheduled(
            initialDelay = 5000,
            fixedDelay = 5000
    )
    public void refreshPendingCounts() {
        stockRestorePendingCount.set(
                getPendingCount(
                        STOCK_RESTORE_STREAM,
                        STOCK_RESTORE_GROUP
                )
        );

        paymentCompensationPendingCount.set(
                getPendingCount(
                        PAYMENT_COMPENSATION_STREAM,
                        PAYMENT_COMPENSATION_GROUP
                )
        );

        stockRestoreConsumerLag.set(
                getConsumerLag(
                        STOCK_RESTORE_STREAM,
                        STOCK_RESTORE_GROUP
                )
        );

        paymentCompensationConsumerLag.set(
                getConsumerLag(
                        PAYMENT_COMPENSATION_STREAM,
                        PAYMENT_COMPENSATION_GROUP
                )
        );
    }

    private long getPendingCount(
            String stream,
            String group
    ) {
        var summary = redisTemplate.opsForStream()
                .pending(stream, group);

        return summary == null
                ? 0L
                : summary.getTotalPendingMessages();
    }

    public void recordStockRestoreRetry() {
        stockRestoreRetryCounter.increment();
    }

    public void recordPaymentCompensationRetry() {
        paymentCompensationRetryCounter.increment();
    }

    public void recordStockRestoreProcessingFailure() {
        stockRestoreProcessingFailureCounter.increment();
    }

    public void recordPaymentCompensationProcessingFailure() {
        paymentCompensationProcessingFailureCounter.increment();
    }

    public void recordPaymentCompensationSuccess() {
        paymentCompensationSuccessCounter.increment();
    }

    public void recordPaymentCompensationFailure() {
        paymentCompensationFailureCounter.increment();
    }

    public Timer.Sample startConsumerProcessingTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordConsumerProcessingTime(
            Timer.Sample sample,
            String stream,
            String result
    ) {
        sample.stop(
                registerProcessingTimer(
                        stream,
                        result
                )
        );
    }

    private Timer registerProcessingTimer(
            String stream,
            String result
    ) {
        return Timer.builder(
                        "gudit.redis.stream.consumer.processing"
                )
                .description(
                        "Redis Stream consumer processing time"
                )
                .tag("stream", stream)
                .tag("result", result)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private void registerLagGauge(
            MeterRegistry meterRegistry,
            AtomicLong value,
            String stream
    ) {
        Gauge.builder(
                        "gudit.redis.stream.consumer.lag",
                        value,
                        AtomicLong::get
                )
                .description(
                        "Number of Redis Stream messages not yet delivered to the consumer group"
                )
                .tag("stream", stream)
                .register(meterRegistry);
    }

    private long getConsumerLag(
            String stream,
            String groupName
    ) {
        var groups = redisTemplate.opsForStream()
                .groups(stream);

        if (groups == null) {
            return 0L;
        }

        return groups.stream()
                .filter(group ->
                        groupName.equals(group.groupName())
                )
                .findFirst()
                .map(group ->
                        parseLag(
                                group.getRaw().get("lag")
                        )
                )
                .orElse(0L);
    }

    private long parseLag(Object lag) {
        if (lag == null) {
            return 0L;
        }

        if (lag instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(lag.toString());
    }
}