package com.team3.gudit.outbox.dto;

public record PaymentCompensationEventPayload(
        Long paymentId,
        String orderId,
        String paymentKey
) {
}