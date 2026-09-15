package com.team3.gudit.cs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CsInquiryRequest(

        @NotBlank(message = "문의 내용을 입력해주세요.")
        @Size(
                max = 1000,
                message = "문의 내용은 1,000자 이하로 입력해주세요."
        )
        String message

) {
}