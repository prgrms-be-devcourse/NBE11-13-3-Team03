package com.team3.gudit.sale.service

interface InventoryService {
    fun decreaseStock(
        saleId: Long?,
        userId: Long?,
        quantity: Int,
    )

    fun restoreStock(
        saleId: Long?,
        userId: Long?,
        quantity: Int,
    )

    fun restoreStockIdempotently(
        eventId: String?,
        saleId: Long?,
        userId: Long?,
        quantity: Int,
    )
}
