package com.team3.gudit.sale.dto.reqeust

import com.team3.gudit.sale.domain.enums.SaleStatus
import jakarta.validation.constraints.NotNull

@JvmRecord
data class SaleStatusUpdateRequestDto(
    @field:NotNull(message = "판매 상태는 필수입니다.")
    val status: SaleStatus?,
)
