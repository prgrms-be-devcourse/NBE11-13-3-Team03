package com.team3.gudit.goods.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import com.fasterxml.jackson.annotation.JsonFormat
import com.team3.gudit.goods.constant.DateformatConstant
import com.team3.gudit.goods.domain.enums.GoodsStatus
import java.time.LocalDateTime

@JvmRecord
data class GoodsListResponse(
    val id: Long?,
    val name: String?,
    val price: Int?,
    val imageUrl: String?,
    val status: GoodsStatus?,
    @get:Schema(type = "string", format = "yyyy-MM-dd HH:mm:ss", example = "2026-09-17 10:00:00", description = "yyyy-MM-dd HH:mm:ss 형식")
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    @get:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    val createdAt: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun builder(): GoodsListResponseBuilder = GoodsListResponseBuilder()
    }

    class GoodsListResponseBuilder {
        private var id: Long? = null
        private var name: String? = null
        private var price: Int? = null
        private var imageUrl: String? = null
        private var status: GoodsStatus? = null
        private var createdAt: LocalDateTime? = null

        fun id(id: Long?): GoodsListResponseBuilder = apply { this.id = id }

        fun name(name: String?): GoodsListResponseBuilder = apply { this.name = name }

        fun price(price: Int?): GoodsListResponseBuilder = apply { this.price = price }

        fun imageUrl(imageUrl: String?): GoodsListResponseBuilder = apply { this.imageUrl = imageUrl }

        fun status(status: GoodsStatus?): GoodsListResponseBuilder = apply { this.status = status }

        fun createdAt(createdAt: LocalDateTime?): GoodsListResponseBuilder = apply { this.createdAt = createdAt }

        fun build(): GoodsListResponse =
            GoodsListResponse(
                id,
                name,
                price,
                imageUrl,
                status,
                createdAt,
            )
    }
}
