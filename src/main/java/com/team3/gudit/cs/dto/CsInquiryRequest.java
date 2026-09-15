package com.team3.gudit.cs.dto;

import jakarta.validation.constraints.NotBlank;

public record CsInquiryRequest(
        @NotBlank(message = "문의 내용을 입력해주세요.")
        String message
) {
}