package com.team3.gudit.outbox.dto

data class StockRestoreEventPayload(
    val purchaseId: Long?,
    val saleId: Long?,
    val userId: Long?,
    val quantity: Int
)