package com.team3.gudit.sale.dto.reqeust

import io.swagger.v3.oas.annotations.media.Schema
import com.fasterxml.jackson.annotation.JsonFormat
import com.team3.gudit.goods.constant.DateformatConstant
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

@JvmRecord
data class SaleUpdateRequestDto(
    @field:NotNull(message = "초기 재고는 필수입니다.")
    @field:Positive(message = "초기 재고는 1개 이상이어야 합니다.")
    val initialStock: Int?,
    @field:NotNull(message = "1인당 최대 구매 수량은 필수입니다.")
    @field:Positive(message = "1인당 최대 구매 수량은 1개 이상이어야 합니다.")
    val maxPurchaseQuantity: Int?,
    @field:NotNull(message = "판매 시작 시간은 필수입니다.")
    @get:Schema(type = "string", format = "yyyy-MM-dd HH:mm:ss", example = "2026-09-17 10:00:00", description = "yyyy-MM-dd HH:mm:ss 형식")
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    @param:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    val startAt: LocalDateTime?,
    @field:NotNull(message = "판매 종료 시간은 필수입니다.")
    @get:Schema(type = "string", format = "yyyy-MM-dd HH:mm:ss", example = "2026-09-17 10:00:00", description = "yyyy-MM-dd HH:mm:ss 형식")
    @field:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    @param:JsonFormat(pattern = DateformatConstant.DATE_FORMAT)
    val endAt: LocalDateTime?,
)
