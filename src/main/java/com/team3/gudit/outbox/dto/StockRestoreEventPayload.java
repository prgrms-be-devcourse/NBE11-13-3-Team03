package com.team3.gudit.outbox.dto;

public record StockRestoreEventPayload(
        Long purchaseId,
        Long saleId,
        Long userId,
        int quantity
) {
}