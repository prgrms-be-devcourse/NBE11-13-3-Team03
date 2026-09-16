package com.team3.gudit.payment.dto

import java.time.OffsetDateTime

@JvmRecord
data class TossPaymentResponse(
    val paymentKey: String?,
    val orderId: String?,
    val status: String?,
    val totalAmount: Int?,
    val approvedAt: OffsetDateTime?
)
