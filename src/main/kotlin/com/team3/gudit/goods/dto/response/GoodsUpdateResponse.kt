package com.team3.gudit.goods.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.team3.gudit.goods.constant.DateformatConstant
import com.team3.gudit.goods.domain.enums.GoodsStatus
import java.time.LocalDateTime

@JvmRecord
data class GoodsUpdateResponse(
    val id: Long?,
    val name: String?,
    val description: String?,
    val price: Int?,
    val imageUrl: String?,
    val status: GoodsStatus?,
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT, timezone = "Asia/Seoul")
    @get:JsonFormat(pattern = DateformatConstant.DATE_FORMAT, timezone = "Asia/Seoul")
    val updatedAt: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun builder(): GoodsUpdateResponseBuilder = GoodsUpdateResponseBuilder()
    }

    class GoodsUpdateResponseBuilder {
        private var id: Long? = null
        private var name: String? = null
        private var description: String? = null
        private var price: Int? = null
        private var imageUrl: String? = null
        private var status: GoodsStatus? = null
        private var updatedAt: LocalDateTime? = null

        fun id(id: Long?): GoodsUpdateResponseBuilder = apply { this.id = id }

        fun name(name: String?): GoodsUpdateResponseBuilder = apply { this.name = name }

        fun description(description: String?): GoodsUpdateResponseBuilder = apply { this.description = description }

        fun price(price: Int?): GoodsUpdateResponseBuilder = apply { this.price = price }

        fun imageUrl(imageUrl: String?): GoodsUpdateResponseBuilder = apply { this.imageUrl = imageUrl }

        fun status(status: GoodsStatus?): GoodsUpdateResponseBuilder = apply { this.status = status }

        fun updatedAt(updatedAt: LocalDateTime?): GoodsUpdateResponseBuilder = apply { this.updatedAt = updatedAt }

        fun build(): GoodsUpdateResponse =
            GoodsUpdateResponse(
                id,
                name,
                description,
                price,
                imageUrl,
                status,
                updatedAt,
            )
    }
}
