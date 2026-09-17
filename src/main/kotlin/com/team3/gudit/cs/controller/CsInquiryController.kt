package com.team3.gudit.cs.controller

import com.team3.gudit.global.exception.ErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import com.team3.gudit.auth.security.CustomUserDetails
import com.team3.gudit.cs.dto.CsInquiryRequest
import com.team3.gudit.cs.service.CsInquiryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "CS", description = "구매 관련 고객 문의 API")
@RestController
@RequestMapping("/api/purchases")
class CsInquiryController(
    private val csInquiryService: CsInquiryService
) {
    @Operation(summary = "구매 문의 접수", description = "로그인한 사용자의 구매에 대해 문의를 접수합니다. 문의 내용은 공백이 아닌 1,000자 이하 문자열이어야 합니다.")
    @SecurityRequirement(name = "cookieAuth")
    @ApiResponse(responseCode = "200", description = "문의 전달 성공 (응답 본문 없음)")
    @ApiResponse(responseCode = "400", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "문의 내용 검증 실패")
    @ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자")
    @ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "구매 또는 결제 정보를 찾을 수 없음")
    @PostMapping("/{purchaseId}/cs-inquiries")
    fun submitInquiry(
        @PathVariable purchaseId: Long?,
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @Valid @RequestBody request: CsInquiryRequest
    ): ResponseEntity<Void> {
        csInquiryService.submitInquiry(
            userDetails.userId,
            purchaseId,
            request.message
        )
        return ResponseEntity.ok().build()
    }
}
