package com.team3.gudit.goods.controller

import com.team3.gudit.global.exception.ErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import com.team3.gudit.goods.dto.request.GoodsCreateRequest
import com.team3.gudit.goods.dto.request.GoodsStatusUpdateRequest
import com.team3.gudit.goods.dto.request.GoodsUpdateRequest
import com.team3.gudit.goods.dto.response.GoodsCreateResponse
import com.team3.gudit.goods.dto.response.GoodsDetailResponse
import com.team3.gudit.goods.dto.response.GoodsListResponse
import com.team3.gudit.goods.dto.response.GoodsStatusUpdateResponse
import com.team3.gudit.goods.dto.response.GoodsUpdateResponse
import com.team3.gudit.goods.service.GoodsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@Tag(
    name = "Goods",
    description = "관리자(ADMIN) 전용 상품 등록, 조회, 수정 및 상태 관리 API",
)
@SecurityRequirement(name = "cookieAuth")
@RestController
@RequestMapping("/api/goods")
class GoodsApiController(
    private val goodsService: GoodsService,
) {
    @Operation(
        summary = "상품 등록",
        description = "상품 정보와 선택 이미지 파일을 multipart/form-data 형식으로 등록합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "상품 등록 성공"),
            ApiResponse(responseCode = "400", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "요청 값 또는 이미지 파일 형식이 올바르지 않음"),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
            ApiResponse(responseCode = "403", content = [Content()], description = "상품 등록 권한 없음"),
            ApiResponse(responseCode = "500", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "이미지 저장 실패"),
        ],
    )
    @SecurityRequirement(name = "cookieAuth")
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createGoods(
        @Valid @RequestPart request: GoodsCreateRequest,
        @RequestPart(value = "fileImage", required = false) fileImage: MultipartFile?,
    ): ResponseEntity<GoodsCreateResponse> {
        val response = goodsService.create(request, fileImage)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(
        summary = "판매 가능한 상품 목록 조회",
        description = "관리자(ADMIN)만 활성 상태인 상품 목록을 조회할 수 있습니다. 일반 사용자와 비로그인 사용자는 접근할 수 없습니다.",
    )
    @ApiResponse(responseCode = "200", description = "상품 목록 조회 성공")
    @ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자")
    @ApiResponse(responseCode = "403", content = [Content()], description = "관리자 권한 필요")
    @GetMapping
    fun getGoodsList(): ResponseEntity<List<GoodsListResponse>> {
        val response = goodsService.goodsList()
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "상품 상세 조회",
        description = "관리자(ADMIN)만 활성 상태인 특정 상품의 상세 정보를 조회할 수 있습니다. 일반 사용자와 비로그인 사용자는 접근할 수 없습니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "상품 상세 조회 성공"),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
            ApiResponse(responseCode = "403", content = [Content()], description = "관리자 권한 필요"),
            ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "GOODS_001: 해당 상품을 찾을 수 없음"),
        ],
    )
    @GetMapping("/{goodsId}")
    fun getGoods(@PathVariable goodsId: Long): ResponseEntity<GoodsDetailResponse> {
        val response = goodsService.goodsDetail(goodsId)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "관리자 상품 목록 조회",
        description = "상품 상태와 관계없이 전체 상품 목록을 조회합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "관리자 상품 목록 조회 성공"),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
            ApiResponse(responseCode = "403", content = [Content()], description = "상품 관리 권한 없음"),
        ],
    )
    @SecurityRequirement(name = "cookieAuth")
    @GetMapping("/admin")
    fun getAdminGoodsList(): ResponseEntity<List<GoodsListResponse>> {
        val response = goodsService.adminGoodsList()
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "관리자 상품 상세 조회",
        description = "상품 상태와 관계없이 특정 상품의 상세 정보를 조회합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "관리자 상품 상세 조회 성공"),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
            ApiResponse(responseCode = "403", content = [Content()], description = "상품 관리 권한 없음"),
            ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "GOODS_001: 해당 상품을 찾을 수 없음"),
        ],
    )
    @SecurityRequirement(name = "cookieAuth")
    @GetMapping("/admin/{goodsId}")
    fun getAdminGoods(@PathVariable goodsId: Long?): ResponseEntity<GoodsDetailResponse> {
        val response = goodsService.adminGoodsDetail(goodsId)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "상품 수정",
        description = "상품 정보와 선택 이미지 파일을 multipart/form-data 형식으로 수정합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "상품 수정 성공"),
            ApiResponse(responseCode = "400", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "요청 값 또는 이미지 파일 형식이 올바르지 않음"),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
            ApiResponse(responseCode = "403", content = [Content()], description = "상품 수정 권한 없음"),
            ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "GOODS_001: 해당 상품을 찾을 수 없음"),
            ApiResponse(responseCode = "500", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "이미지 저장 실패"),
        ],
    )
    @SecurityRequirement(name = "cookieAuth")
    @PutMapping(
        value = ["/{goodsId}"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    fun updateGoods(
        @PathVariable goodsId: Long?,
        @Valid @RequestPart request: GoodsUpdateRequest,
        @RequestPart(value = "fileImage", required = false) fileImage: MultipartFile?,
    ): ResponseEntity<GoodsUpdateResponse> {
        val response = goodsService.updateGoods(goodsId, request, fileImage)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "상품 상태 변경",
        description = "특정 상품의 활성 상태를 변경합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "상품 상태 변경 성공"),
            ApiResponse(responseCode = "400", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "요청한 상품 상태가 올바르지 않음"),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
            ApiResponse(responseCode = "403", content = [Content()], description = "상품 상태 변경 권한 없음"),
            ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "GOODS_001: 해당 상품을 찾을 수 없음"),
        ],
    )
    @SecurityRequirement(name = "cookieAuth")
    @PatchMapping("/{goodsId}/status")
    fun updateGoodsStatus(
        @PathVariable goodsId: Long?,
        @Valid @RequestBody request: GoodsStatusUpdateRequest,
    ): ResponseEntity<GoodsStatusUpdateResponse> {
        val response = goodsService.updateGoodsStatus(goodsId, request)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "상품 삭제",
        description = "특정 상품을 비활성 상태로 변경하여 삭제 처리합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "상품 삭제 성공"),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "인증되지 않은 사용자"),
            ApiResponse(responseCode = "403", content = [Content()], description = "상품 삭제 권한 없음"),
            ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "GOODS_001: 해당 상품을 찾을 수 없음"),
        ],
    )
    @SecurityRequirement(name = "cookieAuth")
    @DeleteMapping("/{goodsId}")
    fun deleteGoods(@PathVariable goodsId: Long?): ResponseEntity<Void> {
        goodsService.deleteGoods(goodsId)
        return ResponseEntity.noContent().build()
    }
}
