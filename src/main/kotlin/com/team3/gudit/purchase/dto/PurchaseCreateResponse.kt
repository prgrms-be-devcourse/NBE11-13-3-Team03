package com.team3.gudit.purchase.dto

import com.team3.gudit.purchase.entity.PurchaseStatus
import java.time.LocalDateTime

@JvmRecord
data class PurchaseCreateResponse(
    val purchaseId: Long?,
    val saleId: Long?,
    val quantity: Int,
    val purchasePrice: Int,
    val status: PurchaseStatus?,
    val purchasedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val orderId: String?
)
