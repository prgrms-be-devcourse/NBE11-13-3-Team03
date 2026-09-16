package com.team3.gudit.sale.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.team3.gudit.goods.constant.DateformatConstant
import com.team3.gudit.sale.domain.entity.Sale
import java.time.LocalDateTime

@JvmRecord
data class SaleCreateResponseDto(
    val goodsId: Long?,
    val initialStock: Int?,
    val maxPurchaseQuantity: Int?,
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    @get:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    val startAt: LocalDateTime?,
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    @get:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    val endAt: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun from(sale: Sale): SaleCreateResponseDto {
            val goods = sale.goods

            return SaleCreateResponseDto(
                goods.id,
                sale.initialStock,
                sale.maxPurchaseQuantity,
                sale.startAt,
                sale.endAt,
            )
        }
    }
}
