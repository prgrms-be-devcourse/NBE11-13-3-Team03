package com.team3.gudit.purchase.dto

import com.team3.gudit.purchase.entity.PurchaseStatus
import java.time.LocalDateTime

@JvmRecord
data class PurchaseDetailResponse(
    val purchaseId: Long?,
    val saleId: Long?,
    val goodsId: Long?,
    val goodsName: String?,
    val imageUrl: String?,
    val quantity: Int,
    val purchasePrice: Int,
    val status: PurchaseStatus?,
    val purchasedAt: LocalDateTime?,
    val canceledAt: LocalDateTime?
)
