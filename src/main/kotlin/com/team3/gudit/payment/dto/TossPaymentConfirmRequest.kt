package com.team3.gudit.payment.dto

@JvmRecord
data class TossPaymentConfirmRequest(
    val paymentKey: String?,
    val orderId: String?,
    val amount: Int
)
