package com.team3.gudit.purchase.controller

import com.team3.gudit.global.exception.ErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import com.team3.gudit.auth.security.CustomUserDetails
import com.team3.gudit.purchase.dto.PurchaseCancelResponse
import com.team3.gudit.purchase.dto.PurchaseCreateResponse
import com.team3.gudit.purchase.dto.PurchaseDetailResponse
import com.team3.gudit.purchase.dto.PurchaseListResponse
import com.team3.gudit.purchase.service.PurchaseService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Purchase", description = "굿즈 구매 및 구매 내역 관리 API")
@RestController
class PurchaseController(private val purchaseService: PurchaseService) {

    @Operation(
        summary = "상품 구매",
        description = "특정 판매 상품을 구매합니다.\n\n로그인한 사용자를 기준으로 구매를 처리하며,\n판매 기간, 판매 상태, 재고 및 중복 구매 여부를 검증합니다.\n"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "구매 성공"),
        ApiResponse(
            responseCode = "400", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            description = "구매 요청 실패\n\n- SALE_001: 상품 판매 기간이 아님\n- SALE_002: 재고 부족\n- SALE_003: 최대 구매 가능 수량 초과\n- SALE_004: 판매 중인 상품이 아님\n"
        ),
        ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
        ApiResponse(
            responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            description = "판매 상품을 찾을 수 없음\n\n- SALE_005: 해당 판매 상품을 찾을 수 없음\n"
        ),
        ApiResponse(
            responseCode = "409", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            description = "중복 구매\n\n- PURCHASE_002: 이미 구매한 판매 상품\n"
        )
    ])
    @SecurityRequirement(name = "cookieAuth")
    @PostMapping("/api/sales/{saleId}/purchases")
    fun purchase(@PathVariable saleId: Long, @AuthenticationPrincipal userDetails: CustomUserDetails): ResponseEntity<PurchaseCreateResponse> {
        val response = purchaseService.purchase(userDetails.userId, saleId)

        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "내 구매 내역 조회",
        description = "현재 로그인한 사용자의 구매 내역을 조회합니다.\n"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "구매 내역 조회 성공"),
        ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자")
    ])
    @SecurityRequirement(name = "cookieAuth")
    @GetMapping("/api/purchases")
    fun getMyPurchases(@AuthenticationPrincipal userDetails: CustomUserDetails): ResponseEntity<PurchaseListResponse> {
        val response = purchaseService.getMyPurchases(userDetails.userId)

        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "구매 상세 조회",
        description = "특정 구매 내역의 상세 정보를 조회합니다.\n\n로그인한 사용자의 구매 내역을 기준으로 조회합니다.\n"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "구매 상세 조회 성공"),
        ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
        ApiResponse(
            responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            description = "구매 내역을 찾을 수 없음\n\n- PURCHASE_001: 구매 내역을 찾을 수 없음\n"
        )
    ])
    @SecurityRequirement(name = "cookieAuth")
    @GetMapping("/api/purchases/{purchaseId}")
    fun getPurchase(@PathVariable purchaseId: Long, @AuthenticationPrincipal userDetails: CustomUserDetails): ResponseEntity<PurchaseDetailResponse> {
        val response = purchaseService.getPurchase(userDetails.userId, purchaseId)

        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "구매 취소",
        description = "특정 구매를 취소합니다.\n\n로그인한 사용자의 구매 내역을 기준으로 취소하며,\n이미 취소된 구매는 다시 취소할 수 없습니다. 판매 종료 시각으로부터 1일이 지나면 취소할 수 없습니다.\n"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "구매 취소 성공"),
        ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
        ApiResponse(
            responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            description = "구매 내역을 찾을 수 없음\n\n- PURCHASE_001: 구매 내역을 찾을 수 없음\n"
        ),
        ApiResponse(
            responseCode = "409", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            description = "구매 취소 충돌\n\n- PURCHASE_003: 이미 취소된 구매\n- PURCHASE_005: 구매 취소 가능 기간 종료\n"
        )
    ])
    @SecurityRequirement(name = "cookieAuth")
    @PostMapping("/api/purchases/{purchaseId}/cancel")
    fun cancel(@PathVariable purchaseId: Long, @AuthenticationPrincipal userDetails: CustomUserDetails): ResponseEntity<PurchaseCancelResponse> {
        val response = purchaseService.cancel(userDetails.userId, purchaseId)

        return ResponseEntity.ok(response)
    }
}
