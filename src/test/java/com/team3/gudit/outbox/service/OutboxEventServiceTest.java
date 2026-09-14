package com.team3.gudit.outbox.service;

import com.team3.gudit.outbox.entity.OutboxEvent;
import com.team3.gudit.outbox.entity.OutboxEventStatus;
import com.team3.gudit.outbox.entity.OutboxEventType;
import com.team3.gudit.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxEventService outboxEventService;

    @BeforeEach
    void setUp() {
        outboxEventService = new OutboxEventService(
                outboxEventRepository,
                new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("재고 복구 요청을 PENDING 상태의 Outbox 이벤트로 저장한다")
    void saveStockRestoreRequested() {
        // given
        Long purchaseId = 1L;
        Long saleId = 2L;
        Long userId = 3L;
        int quantity = 1;

        MDC.put("traceId", "trace-123");

        // when
        outboxEventService.saveStockRestoreRequested(
                purchaseId,
                saleId,
                userId,
                quantity
        );

        // then
        ArgumentCaptor<OutboxEvent> captor =
                ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent savedEvent = captor.getValue();

        assertThat(savedEvent.getEventId()).isNotBlank();
        assertThat(savedEvent.getTraceId()).isEqualTo("trace-123");
        assertThat(savedEvent.getEventType())
                .isEqualTo(OutboxEventType.STOCK_RESTORE_REQUESTED);
        assertThat(savedEvent.getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);

        assertThat(savedEvent.getPayload())
                .contains("\"purchaseId\":1")
                .contains("\"saleId\":2")
                .contains("\"userId\":3")
                .contains("\"quantity\":1");
    }

    @Test
    @DisplayName("traceId가 없어도 재고 복구 Outbox 이벤트를 저장한다")
    void saveStockRestoreRequestedWithoutTraceId() {
        // given
        Long purchaseId = 1L;
        Long saleId = 2L;
        Long userId = 3L;
        int quantity = 1;

        // when
        outboxEventService.saveStockRestoreRequested(
                purchaseId,
                saleId,
                userId,
                quantity
        );

        // then
        ArgumentCaptor<OutboxEvent> captor =
                ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent savedEvent = captor.getValue();

        assertThat(savedEvent.getTraceId()).isNull();
        assertThat(savedEvent.getEventType())
                .isEqualTo(OutboxEventType.STOCK_RESTORE_REQUESTED);
        assertThat(savedEvent.getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
    }
}