package com.team3.gudit.goods.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.team3.gudit.goods.constant.DateformatConstant
import com.team3.gudit.goods.domain.enums.GoodsStatus
import java.time.LocalDateTime

@JvmRecord
data class GoodsDetailResponse(
    val goodsId: Long?,
    val name: String?,
    val description: String?,
    val price: Int?,
    val imageUrl: String?,
    val status: GoodsStatus?,
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT, timezone = "Asia/Seoul")
    @get:JsonFormat(pattern = DateformatConstant.DATE_FORMAT, timezone = "Asia/Seoul")
    val createdAt: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun builder(): GoodsDetailResponseBuilder = GoodsDetailResponseBuilder()
    }

    class GoodsDetailResponseBuilder {
        private var goodsId: Long? = null
        private var name: String? = null
        private var description: String? = null
        private var price: Int? = null
        private var imageUrl: String? = null
        private var status: GoodsStatus? = null
        private var createdAt: LocalDateTime? = null

        fun goodsId(goodsId: Long?): GoodsDetailResponseBuilder = apply { this.goodsId = goodsId }

        fun name(name: String?): GoodsDetailResponseBuilder = apply { this.name = name }

        fun description(description: String?): GoodsDetailResponseBuilder = apply { this.description = description }

        fun price(price: Int?): GoodsDetailResponseBuilder = apply { this.price = price }

        fun imageUrl(imageUrl: String?): GoodsDetailResponseBuilder = apply { this.imageUrl = imageUrl }

        fun status(status: GoodsStatus?): GoodsDetailResponseBuilder = apply { this.status = status }

        fun createdAt(createdAt: LocalDateTime?): GoodsDetailResponseBuilder = apply { this.createdAt = createdAt }

        fun build(): GoodsDetailResponse =
            GoodsDetailResponse(
                goodsId,
                name,
                description,
                price,
                imageUrl,
                status,
                createdAt,
            )
    }
}
