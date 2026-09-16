package com.team3.gudit.purchase.dto

import com.team3.gudit.purchase.entity.PurchaseStatus
import java.time.LocalDateTime

@JvmRecord
data class PurchaseCancelResponse(val purchaseId: Long?, val status: PurchaseStatus?, val canceledAt: LocalDateTime?)
