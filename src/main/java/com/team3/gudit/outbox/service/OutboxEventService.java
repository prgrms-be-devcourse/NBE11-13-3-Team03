package com.team3.gudit.outbox.service;

import com.team3.gudit.outbox.dto.StockRestoreEventPayload;
import com.team3.gudit.outbox.entity.OutboxEvent;
import com.team3.gudit.outbox.entity.OutboxEventType;
import com.team3.gudit.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void saveStockRestoreRequested(
            Long purchaseId,
            Long saleId,
            Long userId,
            int quantity
    ) {
        StockRestoreEventPayload payload = new StockRestoreEventPayload(
                purchaseId,
                saleId,
                userId,
                quantity
        );

        OutboxEvent event = OutboxEvent.create(
                MDC.get("traceId"),
                OutboxEventType.STOCK_RESTORE_REQUESTED,
                serialize(payload)
        );

        outboxEventRepository.save(event);
    }

    private String serialize(StockRestoreEventPayload payload) {
        return objectMapper.writeValueAsString(payload);
    }
}