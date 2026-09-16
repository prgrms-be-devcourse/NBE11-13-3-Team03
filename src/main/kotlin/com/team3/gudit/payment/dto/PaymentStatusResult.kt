package com.team3.gudit.payment.dto

import com.team3.gudit.payment.entity.PaymentStatus
import com.team3.gudit.purchase.entity.PurchaseStatus

@JvmRecord
data class PaymentStatusResult(
    val orderId: String?,
    val purchaseId: Long?,
    val purchaseStatus: PurchaseStatus?,
    val paymentStatus: PaymentStatus?,
    val amount: Int
)
