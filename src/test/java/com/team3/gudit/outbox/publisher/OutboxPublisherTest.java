package com.team3.gudit.outbox.publisher;

import com.team3.gudit.outbox.entity.OutboxEvent;
import com.team3.gudit.outbox.entity.OutboxEventStatus;
import com.team3.gudit.outbox.entity.OutboxEventType;
import com.team3.gudit.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(
                outboxEventRepository,
                stringRedisTemplate
        );
    }

    @Test
    @DisplayName("PENDING Outbox 이벤트를 Redis Stream에 발행하고 PUBLISHED 상태로 변경한다")
    void publishPendingEvent() {
        // given
        OutboxEvent event = OutboxEvent.create(
                "trace-123",
                OutboxEventType.STOCK_RESTORE_REQUESTED,
                """
                {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
                """
        );

        when(outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING
        )).thenReturn(List.of(event));

        when(stringRedisTemplate.opsForStream())
                .thenReturn(streamOperations);

        when(streamOperations.add(any(MapRecord.class)))
                .thenReturn(RecordId.of("1-0"));

        ArgumentCaptor<MapRecord<String, String, String>> captor =
                ArgumentCaptor.forClass(MapRecord.class);

        // when
        outboxPublisher.publishPendingEvents();

        // then
        verify(streamOperations).add(captor.capture());

        MapRecord<String, String, String> record = captor.getValue();

        assertThat(record.getStream())
                .isEqualTo(StockRestoreStreamConstants.STOCK_RESTORE_STREAM);

        assertThat(record.getValue())
                .containsEntry("eventId", event.getEventId())
                .containsEntry("traceId", "trace-123")
                .containsEntry(
                        "eventType",
                        OutboxEventType.STOCK_RESTORE_REQUESTED.name()
                )
                .containsEntry(
                        "payload",
                        """
                        {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
                        """
                );

        assertThat(event.getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("Redis Stream 발행에 실패하면 Outbox 이벤트를 PENDING 상태로 유지한다")
    void keepPendingWhenPublishFails() {
        // given
        OutboxEvent event = OutboxEvent.create(
                "trace-123",
                OutboxEventType.STOCK_RESTORE_REQUESTED,
                """
                {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
                """
        );

        when(outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING
        )).thenReturn(List.of(event));

        when(stringRedisTemplate.opsForStream())
                .thenReturn(streamOperations);

        when(streamOperations.add(any(MapRecord.class)))
                .thenThrow(new RuntimeException("Redis connection failed"));

        // when
        outboxPublisher.publishPendingEvents();

        // then
        assertThat(event.getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("PENDING Outbox 이벤트가 없으면 Redis Stream에 발행하지 않는다")
    void doNothingWhenNoPendingEvents() {
        // given
        when(outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING
        )).thenReturn(List.of());

        // when
        outboxPublisher.publishPendingEvents();

        // then
        verify(stringRedisTemplate, never()).opsForStream();
    }

    @Test
    @DisplayName("Redis Stream 발행 실패 후 다음 실행에서 재발행에 성공하면 PUBLISHED로 변경한다")
    void retryPublishAfterFailure() {
        // given
        OutboxEvent event = OutboxEvent.create(
                "trace-123",
                OutboxEventType.STOCK_RESTORE_REQUESTED,
                """
                {"purchaseId":1,"saleId":2,"userId":3,"quantity":1}
                """
        );

        when(outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING
        )).thenReturn(
                List.of(event),
                List.of(event)
        );

        when(stringRedisTemplate.opsForStream())
                .thenReturn(streamOperations);

        when(streamOperations.add(any(MapRecord.class)))
                .thenThrow(new RuntimeException("Redis connection failed"))
                .thenReturn(RecordId.of("1-0"));

        // when - 첫 번째 발행 실패
        outboxPublisher.publishPendingEvents();

        // then
        assertThat(event.getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);

        // when - 다음 스케줄에서 재발행
        outboxPublisher.publishPendingEvents();

        // then
        assertThat(event.getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);

        verify(streamOperations, times(2))
                .add(any(MapRecord.class));
    }
}