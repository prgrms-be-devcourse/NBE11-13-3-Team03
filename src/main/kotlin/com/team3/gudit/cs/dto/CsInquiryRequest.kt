package com.team3.gudit.cs.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@JvmRecord
data class CsInquiryRequest(
    @field:NotBlank(message = "문의 내용을 입력해주세요.")
    @field:Size(
        max = 1000,
        message = "문의 내용은 1,000자 이하로 입력해주세요."
    )
    val message: String?
)
