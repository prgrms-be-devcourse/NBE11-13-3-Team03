package com.team3.gudit.payment.dto

import java.time.LocalDateTime

@JvmRecord
data class TossPaymentWebhookRequest(
    val eventType: String?,
    val createdAt: LocalDateTime?,
    val data: TossPaymentResponse?
)
