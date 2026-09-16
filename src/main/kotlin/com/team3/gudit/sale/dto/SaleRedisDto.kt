package com.team3.gudit.sale.dto

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.global.exception.GlobalErrorCode
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.enums.SaleStatus
import java.time.ZoneId

class SaleRedisDto(
    val saleId: Long?,
    val startAtMilli: Long?,
    val endAtMilli: Long?,
    val maxPurchaseQuantity: Int?,
    val status: SaleStatus?,
) {
    constructor() : this(null, null, null, null, null)

    companion object {
        @JvmStatic
        fun builder(): SaleRedisDtoBuilder = SaleRedisDtoBuilder()

        @JvmStatic
        fun from(sale: Sale): SaleRedisDto {
            val zoneId = ZoneId.systemDefault()
            val startAt = sale.startAt
            val endAt = sale.endAt

            return SaleRedisDto(
                sale.id,
                startAt.atZone(zoneId).toInstant().toEpochMilli(),
                endAt.atZone(zoneId).toInstant().toEpochMilli(),
                sale.maxPurchaseQuantity,
                sale.status,
            )
        }
    }

    fun toHashFields(): Map<String, String> {
        val fields = HashMap<String, String>()
        fields["startAt"] = startAtMilli.toString()
        fields["endAt"] = endAtMilli.toString()
        fields["maxPurchaseQuantity"] = maxPurchaseQuantity.toString()
        fields["status"] = status?.name ?: throw BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR)
        return fields
    }

    class SaleRedisDtoBuilder {
        private var saleId: Long? = null
        private var startAtMilli: Long? = null
        private var endAtMilli: Long? = null
        private var maxPurchaseQuantity: Int? = null
        private var status: SaleStatus? = null

        fun saleId(saleId: Long?): SaleRedisDtoBuilder = apply { this.saleId = saleId }

        fun startAtMilli(startAtMilli: Long?): SaleRedisDtoBuilder = apply { this.startAtMilli = startAtMilli }

        fun endAtMilli(endAtMilli: Long?): SaleRedisDtoBuilder = apply { this.endAtMilli = endAtMilli }

        fun maxPurchaseQuantity(maxPurchaseQuantity: Int?): SaleRedisDtoBuilder =
            apply { this.maxPurchaseQuantity = maxPurchaseQuantity }

        fun status(status: SaleStatus?): SaleRedisDtoBuilder = apply { this.status = status }

        fun build(): SaleRedisDto =
            SaleRedisDto(
                saleId,
                startAtMilli,
                endAtMilli,
                maxPurchaseQuantity,
                status,
            )
    }
}
