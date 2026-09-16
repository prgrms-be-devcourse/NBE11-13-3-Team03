package com.team3.gudit.outbox.dto

data class PaymentCompensationEventPayload(
    val paymentId: Long?,
    val orderId: String,
    val paymentKey: String
)