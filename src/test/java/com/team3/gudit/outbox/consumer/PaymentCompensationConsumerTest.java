package com.team3.gudit.outbox.consumer;

import com.team3.gudit.payment.dto.TossPaymentResponse;
import com.team3.gudit.payment.service.PaymentService;
import com.team3.gudit.payment.service.PaymentTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.team3.gudit.outbox.publisher.PaymentCompensationStreamConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCompensationConsumerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentTransactionService paymentTransactionService;

    private ObjectMapper objectMapper;

    private PaymentCompensationConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        consumer = new PaymentCompensationConsumer(
                stringRedisTemplate,
                objectMapper,
                paymentService,
                paymentTransactionService
        );

        when(stringRedisTemplate.opsForStream())
                .thenReturn(streamOperations);
    }

    @Test
    void DONE_상태면_Toss_취소후_내부_보상을_처리하고_ACK한다() {
        // given
        String paymentKey = "payment-key-1";

        MapRecord<String, Object, Object> record =
                createRecord(
                        """
                        {
                          "paymentId":1,
                          "orderId":"order-1",
                          "paymentKey":"payment-key-1"
                        }
                        """
                );

        TossPaymentResponse response =
                new TossPaymentResponse(
                        paymentKey,
                        "order-1",
                        "DONE",
                        10000,
                        OffsetDateTime.now()
                );

        mockRead(record);

        when(paymentService.getPayment(paymentKey))
                .thenReturn(response);

        // when
        consumer.consume();

        // then
        verify(paymentService)
                .getPayment(paymentKey);

        verify(paymentService)
                .cancelPayment(paymentKey);

        verify(paymentTransactionService)
                .compensateApprovalFailure(paymentKey);

        verify(streamOperations)
                .acknowledge(
                        eq(PAYMENT_COMPENSATION_STREAM),
                        eq(PAYMENT_COMPENSATION_GROUP),
                        eq(record.getId())
                );
    }

    @Test
    void CANCELED_상태면_Toss_취소없이_내부_보상만_처리하고_ACK한다() {
        // given
        String paymentKey = "payment-key-1";

        MapRecord<String, Object, Object> record =
                createRecord(
                        """
                        {
                          "paymentId":1,
                          "orderId":"order-1",
                          "paymentKey":"payment-key-1"
                        }
                        """
                );

        TossPaymentResponse response =
                new TossPaymentResponse(
                        paymentKey,
                        "order-1",
                        "CANCELED",
                        10000,
                        OffsetDateTime.now()
                );

        mockRead(record);

        when(paymentService.getPayment(paymentKey))
                .thenReturn(response);

        // when
        consumer.consume();

        // then
        verify(paymentService)
                .getPayment(paymentKey);

        verify(paymentService, never())
                .cancelPayment(anyString());

        verify(paymentTransactionService)
                .compensateApprovalFailure(paymentKey);

        verify(streamOperations)
                .acknowledge(
                        eq(PAYMENT_COMPENSATION_STREAM),
                        eq(PAYMENT_COMPENSATION_GROUP),
                        eq(record.getId())
                );
    }

    @Test
    void Toss_조회가_실패하면_ACK하지_않는다() {
        // given
        String paymentKey = "payment-key-1";

        MapRecord<String, Object, Object> record =
                createRecord(
                        """
                        {
                          "paymentId":1,
                          "orderId":"order-1",
                          "paymentKey":"payment-key-1"
                        }
                        """
                );

        mockRead(record);

        when(paymentService.getPayment(paymentKey))
                .thenThrow(new RuntimeException("Toss 조회 실패"));

        // when
        consumer.consume();

        // then
        verify(paymentService)
                .getPayment(paymentKey);

        verify(paymentService, never())
                .cancelPayment(anyString());

        verify(paymentTransactionService, never())
                .compensateApprovalFailure(anyString());

        verify(streamOperations, never())
                .acknowledge(
                        anyString(),
                        anyString(),
                        any(RecordId.class)
                );
    }

    @Test
    void Toss_취소가_실패하면_ACK하지_않는다() {
        // given
        String paymentKey = "payment-key-1";

        MapRecord<String, Object, Object> record =
                createRecord(
                        """
                        {
                          "paymentId":1,
                          "orderId":"order-1",
                          "paymentKey":"payment-key-1"
                        }
                        """
                );

        TossPaymentResponse response =
                new TossPaymentResponse(
                        paymentKey,
                        "order-1",
                        "DONE",
                        10000,
                        OffsetDateTime.now()
                );

        mockRead(record);

        when(paymentService.getPayment(paymentKey))
                .thenReturn(response);

        when(paymentService.cancelPayment(paymentKey))
                .thenThrow(new RuntimeException("Toss 취소 실패"));

        // when
        consumer.consume();

        // then
        verify(paymentService)
                .cancelPayment(paymentKey);

        verify(paymentTransactionService, never())
                .compensateApprovalFailure(anyString());

        verify(streamOperations, never())
                .acknowledge(
                        anyString(),
                        anyString(),
                        any(RecordId.class)
                );
    }

    @Test
    void 내부_보상_처리가_실패하면_ACK하지_않는다() {
        // given
        String paymentKey = "payment-key-1";

        MapRecord<String, Object, Object> record =
                createRecord(
                        """
                        {
                          "paymentId":1,
                          "orderId":"order-1",
                          "paymentKey":"payment-key-1"
                        }
                        """
                );

        TossPaymentResponse response =
                new TossPaymentResponse(
                        paymentKey,
                        "order-1",
                        "CANCELED",
                        10000,
                        OffsetDateTime.now()
                );

        mockRead(record);

        when(paymentService.getPayment(paymentKey))
                .thenReturn(response);

        doThrow(new RuntimeException("내부 보상 실패"))
                .when(paymentTransactionService)
                .compensateApprovalFailure(paymentKey);

        // when
        consumer.consume();

        // then
        verify(paymentTransactionService)
                .compensateApprovalFailure(paymentKey);

        verify(streamOperations, never())
                .acknowledge(
                        anyString(),
                        anyString(),
                        any(RecordId.class)
                );
    }

    @Test
    void Pending_메시지를_claim한뒤_재처리에_성공하면_ACK한다() {
        // given
        String paymentKey = "payment-key-1";

        MapRecord<String, Object, Object> record =
                createRecord(
                        """
                        {
                          "paymentId":1,
                          "orderId":"order-1",
                          "paymentKey":"payment-key-1"
                        }
                        """
                );

        TossPaymentResponse response =
                new TossPaymentResponse(
                        paymentKey,
                        "order-1",
                        "CANCELED",
                        10000,
                        OffsetDateTime.now()
                );

        PendingMessages pendingMessages =
                mock(PendingMessages.class);

        PendingMessage pendingMessage =
                mock(PendingMessage.class);

        when(pendingMessage.getId())
                .thenReturn(record.getId());

        Iterator<PendingMessage> iterator =
                List.of(pendingMessage).iterator();

        when(pendingMessages.iterator())
                .thenReturn(iterator);

        when(streamOperations.pending(
                eq(PAYMENT_COMPENSATION_STREAM),
                eq(PAYMENT_COMPENSATION_GROUP),
                any(),
                anyLong(),
                any()
        )).thenReturn(pendingMessages);

        when(streamOperations.claim(
                eq(PAYMENT_COMPENSATION_STREAM),
                eq(PAYMENT_COMPENSATION_GROUP),
                eq(PAYMENT_COMPENSATION_CONSUMER),
                any(),
                eq(record.getId())
        )).thenReturn(List.of(record));

        when(paymentService.getPayment(paymentKey))
                .thenReturn(response);

        // when
        consumer.retryPending();

        // then
        verify(streamOperations)
                .claim(
                        eq(PAYMENT_COMPENSATION_STREAM),
                        eq(PAYMENT_COMPENSATION_GROUP),
                        eq(PAYMENT_COMPENSATION_CONSUMER),
                        any(),
                        eq(record.getId())
                );

        verify(paymentTransactionService)
                .compensateApprovalFailure(paymentKey);

        verify(streamOperations)
                .acknowledge(
                        eq(PAYMENT_COMPENSATION_STREAM),
                        eq(PAYMENT_COMPENSATION_GROUP),
                        eq(record.getId())
                );
    }

    @SuppressWarnings({
            "unchecked",
            "rawtypes"
    })
    private void mockRead(
            MapRecord<String, Object, Object> record
    ) {
        when(streamOperations.read(
                any(Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset[].class)
        )).thenReturn(List.of(record));
    }

    @SuppressWarnings({
            "unchecked",
            "rawtypes"
    })
    private MapRecord<String, Object, Object> createRecord(
            String payload
    ) {
        return (MapRecord) StreamRecords
                .newRecord()
                .in(PAYMENT_COMPENSATION_STREAM)
                .ofMap(
                        Map.of(
                                "eventId", "event-123",
                                "traceId", "trace-123",
                                "eventType",
                                "PAYMENT_COMPENSATION_REQUIRED",
                                "payload", payload
                        )
                );
    }
}