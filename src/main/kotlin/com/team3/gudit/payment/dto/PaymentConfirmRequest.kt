package com.team3.gudit.payment.dto

@JvmRecord
data class PaymentConfirmRequest(
    val paymentKey: String?,
    val orderId: String?,
    val amount: Int
)
