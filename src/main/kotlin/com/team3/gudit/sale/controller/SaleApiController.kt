package com.team3.gudit.sale.controller

import com.team3.gudit.global.exception.ErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import com.team3.gudit.auth.security.CustomUserDetails
import com.team3.gudit.sale.dto.reqeust.SaleCreateRequestDto
import com.team3.gudit.sale.dto.reqeust.SaleStatusUpdateRequestDto
import com.team3.gudit.sale.dto.reqeust.SaleUpdateRequestDto
import com.team3.gudit.sale.dto.response.SaleCreateResponseDto
import com.team3.gudit.sale.dto.response.SaleDetailResponseDto
import com.team3.gudit.sale.dto.response.SaleListResponseDto
import com.team3.gudit.sale.dto.response.SaleStatusUpdateResponseDto
import com.team3.gudit.sale.service.SaleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "Sale",
    description = "타임세일 등록, 조회, 수정 및 상태 관리 API",
)
@RestController
@RequestMapping("/api/sales")
class SaleApiController(
    private val saleService: SaleService,
) {
    @Operation(
        summary = "타임세일 등록",
        description = "상품, 판매 기간, 재고 및 1인당 최대 구매 수량을 지정하여 타임세일을 등록합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "타임세일 등록 성공"),
            ApiResponse(
                responseCode = "400", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
                description = "SALE_001, SALE_009, SALE_010: 판매 기간·초기 재고·최대 구매 수량이 올바르지 않음",
            ),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
            ApiResponse(responseCode = "403", content = [Content()], description = "타임세일 등록 권한 없음"),
            ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "GOODS_001: 해당 상품을 찾을 수 없음"),
        ],
    )
    @SecurityRequirement(name = "cookieAuth")
    @PostMapping
    fun createSale(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @Valid @RequestBody request: SaleCreateRequestDto,
    ): ResponseEntity<SaleCreateResponseDto> {
        val response = saleService.createSale(request, userDetails.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(
        summary = "타임세일 상세 조회",
        description = "특정 타임세일의 상품 정보, 판매 상태 및 현재 재고를 조회합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "타임세일 상세 조회 성공"),
            ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "SALE_005: 해당 판매 상품을 찾을 수 없음"),
        ],
    )
    @GetMapping("/{saleId}")
    fun getSaleDetail(@PathVariable saleId: Long): ResponseEntity<SaleDetailResponseDto> {
        val response = saleService.saleDetail(saleId)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "타임세일 목록 조회",
        description = "활성 상품에 연결된 타임세일 목록과 현재 판매 상태를 조회합니다.",
    )
    @ApiResponse(responseCode = "200", description = "타임세일 목록 조회 성공")
    @GetMapping
    fun getSaleList(): ResponseEntity<List<SaleListResponseDto>> {
        val response = saleService.saleList()
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "타임세일 수정",
        description = "판매 대기 상태인 타임세일의 기간, 재고 및 최대 구매 수량을 수정합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "타임세일 수정 성공"),
            ApiResponse(
                responseCode = "400", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
                description = "SALE_001, SALE_006, SALE_009, SALE_010: 수정할 수 없는 상태이거나 요청 값이 올바르지 않음",
            ),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
            ApiResponse(responseCode = "403", content = [Content()], description = "타임세일 수정 권한 없음"),
            ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "SALE_005: 해당 판매 상품을 찾을 수 없음"),
        ],
    )
    @SecurityRequirement(name = "cookieAuth")
    @PutMapping("/{saleId}")
    fun updateSale(
        @PathVariable saleId: Long?,
        @Valid @RequestBody request: SaleUpdateRequestDto,
    ): ResponseEntity<SaleDetailResponseDto> {
        val response = saleService.updateSale(saleId, request)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "타임세일 상태 변경",
        description = "특정 타임세일의 판매 상태를 변경합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "타임세일 상태 변경 성공"),
            ApiResponse(responseCode = "400", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "SALE_008: 올바르지 않은 판매 상태 변경 요청"),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
            ApiResponse(responseCode = "403", content = [Content()], description = "타임세일 상태 변경 권한 없음"),
            ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "SALE_005: 해당 판매 상품을 찾을 수 없음"),
        ],
    )
    @SecurityRequirement(name = "cookieAuth")
    @PatchMapping("/{saleId}/status")
    fun updateSaleStatus(
        @PathVariable saleId: Long?,
        @Valid @RequestBody request: SaleStatusUpdateRequestDto,
    ): ResponseEntity<SaleStatusUpdateResponseDto> {
        val response = saleService.updateSaleStatus(saleId, request)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "타임세일 삭제",
        description = "진행 중이 아닌 타임세일을 소프트 삭제하고 Redis 재고 정보를 제거합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "타임세일 삭제 성공"),
            ApiResponse(responseCode = "400", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "SALE_007: 진행 중인 타임세일은 삭제할 수 없음"),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
            ApiResponse(responseCode = "403", content = [Content()], description = "타임세일 삭제 권한 없음"),
            ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "SALE_005: 해당 판매 상품을 찾을 수 없음"),
        ],
    )
    @SecurityRequirement(name = "cookieAuth")
    @DeleteMapping("/{saleId}")
    fun deleteSale(@PathVariable saleId: Long?): ResponseEntity<Void> {
        saleService.deleteSale(saleId)
        return ResponseEntity.noContent().build()
    }

    // Redis warmup 스케줄러 작동 오류 시 관리자가 수동으로 warmup
    @Operation(
        summary = "타임세일 Redis 수동 Warm-up",
        description = "스케줄러가 Warm-up을 수행하지 못한 경우 판매 대기 상태의 재고와 정책을 Redis에 적재합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Redis Warm-up 성공"),
            ApiResponse(responseCode = "400", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "SALE_013: 판매 대기 상태가 아니어서 Warm-up할 수 없음"),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
            ApiResponse(responseCode = "403", content = [Content()], description = "타임세일 관리 권한 없음"),
            ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "SALE_005: 해당 판매 상품을 찾을 수 없음"),
            ApiResponse(responseCode = "500", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "Redis Warm-up 처리 실패"),
        ],
    )
    @SecurityRequirement(name = "cookieAuth")
    @PostMapping("/{saleId}/warmup")
    fun warmupSale(@PathVariable saleId: Long?): ResponseEntity<String> {
        saleService.warmupSaleInfo(saleId)
        return ResponseEntity.ok("타임세일(id=$saleId) 수동 Warm-up이 완료되었습니다.")
    }
}
