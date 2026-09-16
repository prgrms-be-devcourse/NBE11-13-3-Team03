package com.team3.gudit.sale.dto.response

import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.enums.SaleStatus
import java.time.LocalDateTime

@JvmRecord
data class SaleDetailResponseDto(
    val id: Long?,
    val goodsId: Long?,
    val goodsName: String?,
    val price: Int?,
    val initialStock: Int,
    val remainingStock: Int,
    val maxPurchaseQuantity: Int?,
    val status: SaleStatus?,
    val description: String?,
    val imageUrl: String?,
    val startAt: LocalDateTime?,
    val endAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun builder(): SaleDetailResponseDtoBuilder = SaleDetailResponseDtoBuilder()

        @JvmStatic
        fun from(sale: Sale): SaleDetailResponseDto =
            from(
                sale,
                sale.remainingStock,
                sale.status,
            )

        @JvmStatic
        fun from(
            sale: Sale,
            remainingStock: Int,
            status: SaleStatus?,
        ): SaleDetailResponseDto {
            val goods = sale.goods
            val initialStock = sale.initialStock

            return SaleDetailResponseDto(
                sale.id,
                goods.id,
                goods.name,
                goods.price,
                initialStock,
                remainingStock,
                sale.maxPurchaseQuantity,
                status,
                goods.description,
                goods.imageUrl,
                sale.startAt,
                sale.endAt,
                sale.createdAt,
            )
        }
    }

    class SaleDetailResponseDtoBuilder {
        private var id: Long? = null
        private var goodsId: Long? = null
        private var goodsName: String? = null
        private var price: Int? = null
        private var initialStock: Int = 0
        private var remainingStock: Int = 0
        private var maxPurchaseQuantity: Int? = null
        private var status: SaleStatus? = null
        private var description: String? = null
        private var imageUrl: String? = null
        private var startAt: LocalDateTime? = null
        private var endAt: LocalDateTime? = null
        private var createdAt: LocalDateTime? = null

        fun id(id: Long?): SaleDetailResponseDtoBuilder = apply { this.id = id }

        fun goodsId(goodsId: Long?): SaleDetailResponseDtoBuilder = apply { this.goodsId = goodsId }

        fun goodsName(goodsName: String?): SaleDetailResponseDtoBuilder = apply { this.goodsName = goodsName }

        fun price(price: Int?): SaleDetailResponseDtoBuilder = apply { this.price = price }

        fun initialStock(initialStock: Int): SaleDetailResponseDtoBuilder = apply { this.initialStock = initialStock }

        fun remainingStock(remainingStock: Int): SaleDetailResponseDtoBuilder =
            apply { this.remainingStock = remainingStock }

        fun maxPurchaseQuantity(maxPurchaseQuantity: Int?): SaleDetailResponseDtoBuilder =
            apply { this.maxPurchaseQuantity = maxPurchaseQuantity }

        fun status(status: SaleStatus?): SaleDetailResponseDtoBuilder = apply { this.status = status }

        fun description(description: String?): SaleDetailResponseDtoBuilder = apply { this.description = description }

        fun imageUrl(imageUrl: String?): SaleDetailResponseDtoBuilder = apply { this.imageUrl = imageUrl }

        fun startAt(startAt: LocalDateTime?): SaleDetailResponseDtoBuilder = apply { this.startAt = startAt }

        fun endAt(endAt: LocalDateTime?): SaleDetailResponseDtoBuilder = apply { this.endAt = endAt }

        fun createdAt(createdAt: LocalDateTime?): SaleDetailResponseDtoBuilder = apply { this.createdAt = createdAt }

        fun build(): SaleDetailResponseDto =
            SaleDetailResponseDto(
                id,
                goodsId,
                goodsName,
                price,
                initialStock,
                remainingStock,
                maxPurchaseQuantity,
                status,
                description,
                imageUrl,
                startAt,
                endAt,
                createdAt,
            )
    }
}
