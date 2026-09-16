package com.team3.gudit.sale.dto.response

import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.enums.SaleStatus

@JvmRecord
data class SaleStatusUpdateResponseDto(
    val saleId: Long?,
    val status: SaleStatus?,
) {
    companion object {
        @JvmStatic
        fun from(sale: Sale): SaleStatusUpdateResponseDto =
            SaleStatusUpdateResponseDto(
                sale.id,
                sale.status,
            )
    }
}
