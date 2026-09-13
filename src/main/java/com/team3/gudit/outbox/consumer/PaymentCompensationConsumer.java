package com.team3.gudit.outbox.consumer;

import com.team3.gudit.outbox.dto.PaymentCompensationEventPayload;
import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.service.PaymentService;
import com.team3.gudit.payment.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompensationConsumer {

    private static final int BATCH_SIZE = 10;
    private static final Duration PENDING_MIN_IDLE = Duration.ofSeconds(30);
    private static final int PENDING_BATCH_SIZE = 10;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;
    private final PaymentTransactionService paymentTransactionService;

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        StreamOperations<String, Object, Object> streamOperations =
                stringRedisTemplate.opsForStream();

        List<MapRecord<String, Object, Object>> records =
                streamOperations.read(
                        Consumer.from(
                                PAYMENT_COMPENSATION_GROUP,
                                PAYMENT_COMPENSATION_CONSUMER
                        ),
                        StreamReadOptions.empty()
                                .count(BATCH_SIZE)
                                .block(Duration.ofSeconds(1)),
                        StreamOffset.create(
                                PAYMENT_COMPENSATION_STREAM,
                                ReadOffset.lastConsumed()
                        )
                );

        if (records == null || records.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> record : records) {
            processRecord(record);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void retryPending() {
        StreamOperations<String, Object, Object> streamOperations =
                stringRedisTemplate.opsForStream();

        var pendingMessages =
                streamOperations.pending(
                        PAYMENT_COMPENSATION_STREAM,
                        PAYMENT_COMPENSATION_GROUP,
                        org.springframework.data.domain.Range.unbounded(),
                        PENDING_BATCH_SIZE,
                        PENDING_MIN_IDLE
                );

        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }

        for (var pendingMessage : pendingMessages) {
            List<MapRecord<String, Object, Object>> claimedRecords =
                    streamOperations.claim(
                            PAYMENT_COMPENSATION_STREAM,
                            PAYMENT_COMPENSATION_GROUP,
                            PAYMENT_COMPENSATION_CONSUMER,
                            PENDING_MIN_IDLE,
                            pendingMessage.getId()
                    );

            if (claimedRecords == null || claimedRecords.isEmpty()) {
                continue;
            }

            for (MapRecord<String, Object, Object> record : claimedRecords) {
                processRecord(record);
            }
        }
    }

    private void processRecord(
            MapRecord<String, Object, Object> record
    ) {
        Map<Object, Object> values = record.getValue();

        String traceId = getValue(values, "traceId");
        String payload = getValue(values, "payload");

        try {
            if (traceId != null && !traceId.isBlank()) {
                MDC.put("traceId", traceId);
            }

            PaymentCompensationEventPayload eventPayload =
                    objectMapper.readValue(
                            payload,
                            PaymentCompensationEventPayload.class
                    );

            compensate(eventPayload);

            stringRedisTemplate.opsForStream()
                    .acknowledge(
                            PAYMENT_COMPENSATION_STREAM,
                            PAYMENT_COMPENSATION_GROUP,
                            record.getId()
                    );

        } catch (RuntimeException e) {
            log.warn(
                    "Payment compensation processing failed. recordId={}",
                    record.getId(),
                    e
            );
        } finally {
            MDC.remove("traceId");
        }
    }

    private void compensate(
            PaymentCompensationEventPayload payload
    ) {
        TossPaymentResponse actualPayment =
                paymentService.getPayment(
                        payload.paymentKey()
                );

        switch (actualPayment.status()) {
            case "DONE" -> {
                paymentService.cancelPayment(
                        payload.paymentKey()
                );

                paymentTransactionService
                        .compensateApprovalFailure(
                                payload.paymentKey()
                        );
            }

            case "CANCELED" ->
                    paymentTransactionService
                            .compensateApprovalFailure(
                                    payload.paymentKey()
                            );

            default -> throw new IllegalStateException(
                    "Unsupported Toss payment status for compensation. status="
                            + actualPayment.status()
                            + ", paymentKey="
                            + payload.paymentKey()
            );
        }
    }

    private String getValue(
            Map<Object, Object> values,
            String key
    ) {
        Object value = values.get(key);

        return value == null
                ? null
                : value.toString();
    }
}