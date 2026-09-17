package com.team3.gudit.goods.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import com.fasterxml.jackson.annotation.JsonFormat
import com.team3.gudit.goods.constant.DateformatConstant
import com.team3.gudit.goods.domain.enums.GoodsStatus
import java.time.LocalDateTime

@JvmRecord
data class GoodsCreateResponse(
    val id: Long?,
    val name: String?,
    val description: String?,
    val price: Int?,
    val imageUrl: String?,
    val status: GoodsStatus?,
    @get:Schema(type = "string", format = "yyyy-MM-dd HH:mm:ss", example = "2026-09-17 10:00:00", description = "yyyy-MM-dd HH:mm:ss 형식")
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT, timezone = "Asia/Seoul")
    @get:JsonFormat(pattern = DateformatConstant.DATE_FORMAT, timezone = "Asia/Seoul")
    val createdAt: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun builder(): GoodsCreateResponseBuilder = GoodsCreateResponseBuilder()
    }

    class GoodsCreateResponseBuilder {
        private var id: Long? = null
        private var name: String? = null
        private var description: String? = null
        private var price: Int? = null
        private var imageUrl: String? = null
        private var status: GoodsStatus? = null
        private var createdAt: LocalDateTime? = null

        fun id(id: Long?): GoodsCreateResponseBuilder = apply { this.id = id }

        fun name(name: String?): GoodsCreateResponseBuilder = apply { this.name = name }

        fun description(description: String?): GoodsCreateResponseBuilder = apply { this.description = description }

        fun price(price: Int?): GoodsCreateResponseBuilder = apply { this.price = price }

        fun imageUrl(imageUrl: String?): GoodsCreateResponseBuilder = apply { this.imageUrl = imageUrl }

        fun status(status: GoodsStatus?): GoodsCreateResponseBuilder = apply { this.status = status }

        fun createdAt(createdAt: LocalDateTime?): GoodsCreateResponseBuilder = apply { this.createdAt = createdAt }

        fun build(): GoodsCreateResponse =
            GoodsCreateResponse(
                id,
                name,
                description,
                price,
                imageUrl,
                status,
                createdAt,
            )
    }
}
