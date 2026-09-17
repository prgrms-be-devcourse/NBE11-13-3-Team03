package com.team3.gudit.sale.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import com.fasterxml.jackson.annotation.JsonFormat
import com.team3.gudit.goods.constant.DateformatConstant
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.enums.SaleStatus
import java.time.LocalDateTime

@JvmRecord
data class SaleListResponseDto(
    val saleId: Long?,
    val goodsName: String?,
    val price: Int?,
    val remainingStock: Int?,
    val status: SaleStatus?,
    val description: String?,
    val imageUrl: String?,
    val initialStock: Int?,
    @get:Schema(type = "string", format = "yyyy-MM-dd HH:mm:ss", example = "2026-09-17 10:00:00", description = "yyyy-MM-dd HH:mm:ss 형식")
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    @get:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    val startAt: LocalDateTime?,
    @get:Schema(type = "string", format = "yyyy-MM-dd HH:mm:ss", example = "2026-09-17 10:00:00", description = "yyyy-MM-dd HH:mm:ss 형식")
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    @get:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    val endAt: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun builder(): SaleListResponseDtoBuilder = SaleListResponseDtoBuilder()

        @JvmStatic
        fun from(sale: Sale): SaleListResponseDto =
            from(
                sale,
                sale.remainingStock,
                sale.status,
            )

        @JvmStatic
        fun from(
            sale: Sale,
            remainingStock: Int?,
            status: SaleStatus?,
        ): SaleListResponseDto {
            val goods = sale.goods

            return SaleListResponseDto(
                sale.id,
                goods.name,
                goods.price,
                remainingStock,
                status,
                goods.description,
                goods.imageUrl,
                sale.initialStock,
                sale.startAt,
                sale.endAt,
            )
        }
    }

    class SaleListResponseDtoBuilder {
        private var saleId: Long? = null
        private var goodsName: String? = null
        private var price: Int? = null
        private var remainingStock: Int? = null
        private var status: SaleStatus? = null
        private var description: String? = null
        private var imageUrl: String? = null
        private var initialStock: Int? = null
        private var startAt: LocalDateTime? = null
        private var endAt: LocalDateTime? = null

        fun saleId(saleId: Long?): SaleListResponseDtoBuilder = apply { this.saleId = saleId }

        fun goodsName(goodsName: String?): SaleListResponseDtoBuilder = apply { this.goodsName = goodsName }

        fun price(price: Int?): SaleListResponseDtoBuilder = apply { this.price = price }

        fun remainingStock(remainingStock: Int?): SaleListResponseDtoBuilder =
            apply { this.remainingStock = remainingStock }

        fun status(status: SaleStatus?): SaleListResponseDtoBuilder = apply { this.status = status }

        fun description(description: String?): SaleListResponseDtoBuilder = apply { this.description = description }

        fun imageUrl(imageUrl: String?): SaleListResponseDtoBuilder = apply { this.imageUrl = imageUrl }

        fun initialStock(initialStock: Int?): SaleListResponseDtoBuilder = apply { this.initialStock = initialStock }

        fun startAt(startAt: LocalDateTime?): SaleListResponseDtoBuilder = apply { this.startAt = startAt }

        fun endAt(endAt: LocalDateTime?): SaleListResponseDtoBuilder = apply { this.endAt = endAt }

        fun build(): SaleListResponseDto =
            SaleListResponseDto(
                saleId,
                goodsName,
                price,
                remainingStock,
                status,
                description,
                imageUrl,
                initialStock,
                startAt,
                endAt,
            )
    }
}
