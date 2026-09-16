package com.team3.gudit.goods.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.team3.gudit.goods.constant.DateformatConstant
import com.team3.gudit.goods.domain.enums.GoodsStatus
import java.time.LocalDateTime

@JvmRecord
data class GoodsStatusUpdateResponse(
    val id: Long?,
    val status: GoodsStatus?,
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT, timezone = "Asia/Seoul")
    @get:JsonFormat(pattern = DateformatConstant.DATE_FORMAT, timezone = "Asia/Seoul")
    val updatedAt: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun builder(): GoodsStatusUpdateResponseBuilder = GoodsStatusUpdateResponseBuilder()
    }

    class GoodsStatusUpdateResponseBuilder {
        private var id: Long? = null
        private var status: GoodsStatus? = null
        private var updatedAt: LocalDateTime? = null

        fun id(id: Long?): GoodsStatusUpdateResponseBuilder = apply { this.id = id }

        fun status(status: GoodsStatus?): GoodsStatusUpdateResponseBuilder = apply { this.status = status }

        fun updatedAt(updatedAt: LocalDateTime?): GoodsStatusUpdateResponseBuilder = apply { this.updatedAt = updatedAt }

        fun build(): GoodsStatusUpdateResponse =
            GoodsStatusUpdateResponse(
                id,
                status,
                updatedAt,
            )
    }
}
