package com.team3.gudit.sale.dto.reqeust

import com.fasterxml.jackson.annotation.JsonFormat
import com.team3.gudit.goods.constant.DateformatConstant
import com.team3.gudit.goods.domain.entity.Goods
import com.team3.gudit.sale.domain.entity.Sale
import com.team3.gudit.sale.domain.enums.SaleStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

@JvmRecord
data class SaleCreateRequestDto(
    @field:NotNull(message = "상품 ID는 필수입니다.")
    val goodsId: Long?,
    @field:NotNull(message = "초기 재고는 필수입니다.")
    @field:Positive(message = "초기 재고는 1개 이상이어야 합니다.")
    val initialStock: Int?,
    @field:NotNull(message = "1인당 최대 구매 수량은 필수입니다.")
    @field:Positive(message = "1인당 최대 구매 수량은 1개 이상이어야 합니다.")
    val maxPurchaseQuantity: Int?,
    @field:NotNull(message = "판매 시작 시간은 필수입니다.")
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    @param:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    val startAt: LocalDateTime?,
    @field:NotNull(message = "판매 종료 시간은 필수입니다.")
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    @param:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    val endAt: LocalDateTime?,
) {
    fun toEntity(goods: Goods): Sale =
        Sale.builder()
            .goods(goods)
            .initialStock(initialStock)
            .remainingStock(initialStock)
            .maxPurchaseQuantity(maxPurchaseQuantity)
            .startAt(startAt)
            .endAt(endAt)
            .status(SaleStatus.READY)
            .build()
}
