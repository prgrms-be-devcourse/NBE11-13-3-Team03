package com.team3.gudit.goods.controller

import com.team3.gudit.auth.jwt.TokenProvider
import com.team3.gudit.goods.domain.enums.GoodsStatus
import com.team3.gudit.goods.dto.request.GoodsCreateRequest
import com.team3.gudit.goods.dto.request.GoodsStatusUpdateRequest
import com.team3.gudit.goods.dto.request.GoodsUpdateRequest
import com.team3.gudit.goods.dto.response.GoodsCreateResponse
import com.team3.gudit.goods.dto.response.GoodsUpdateResponse
import com.team3.gudit.goods.service.GoodsService
import com.team3.gudit.testsupport.anyValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

@WebMvcTest(GoodsApiController::class)
@AutoConfigureMockMvc(addFilters = false)
class GoodsApiControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var goodsService: GoodsService

    @MockitoBean
    private lateinit var tokenProvider: TokenProvider

    @Test
    @DisplayName("상품 정보와 이미지를 multipart 요청으로 생성한다")
    fun createGoods() {
        val request = GoodsCreateRequest("테스트 상품", "테스트 설명", 10_000, null)
        val requestPart = jsonPart("request", request)
        val imagePart =
            MockMultipartFile(
                "fileImage",
                "test.png",
                MediaType.IMAGE_PNG_VALUE,
                "image".toByteArray(),
            )
        val response =
            GoodsCreateResponse.builder()
                .id(1L)
                .name("테스트 상품")
                .description("테스트 설명")
                .price(10_000)
                .imageUrl("test.png")
                .status(GoodsStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build()
        given(goodsService.create(anyValue(GoodsCreateRequest::class.java), any())).willReturn(response)

        mockMvc
            .perform(multipart("/api/goods").file(requestPart).file(imagePart))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.name").value("테스트 상품"))
            .andExpect(jsonPath("$.price").value(10_000))

        verify(goodsService).create(anyValue(GoodsCreateRequest::class.java), any())
    }

    @Test
    @DisplayName("상품명이 비어 있으면 상품 생성 요청은 400을 반환한다")
    fun createGoodsWithBlankName() {
        val requestPart = jsonPart("request", GoodsCreateRequest("", "테스트 설명", 10_000, null))

        mockMvc
            .perform(multipart("/api/goods").file(requestPart))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("COMMON_001"))
            .andExpect(jsonPath("$.fieldErrors.name").value("상품명은 필수 입력값입니다."))

        verify(goodsService, never()).create(anyValue(GoodsCreateRequest::class.java), any())
    }

    @Test
    @DisplayName("상품 가격이 음수이면 상품 생성 요청은 400을 반환한다")
    fun createGoodsWithNegativePrice() {
        val requestPart = jsonPart("request", GoodsCreateRequest("테스트 상품", "테스트 설명", -1, null))

        mockMvc
            .perform(multipart("/api/goods").file(requestPart))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("COMMON_001"))
            .andExpect(jsonPath("$.fieldErrors.price").value("가격은 0원 이상이어야 합니다."))

        verify(goodsService, never()).create(anyValue(GoodsCreateRequest::class.java), any())
    }

    @Test
    @DisplayName("상품 전체 수정은 multipart PUT 요청으로 처리한다")
    fun updateGoods() {
        val requestPart = jsonPart("request", GoodsUpdateRequest("수정 상품", "수정 설명", 20_000, null))
        val response =
            GoodsUpdateResponse.builder()
                .id(1L)
                .name("수정 상품")
                .description("수정 설명")
                .price(20_000)
                .imageUrl("test.png")
                .status(GoodsStatus.ACTIVE)
                .updatedAt(LocalDateTime.now())
                .build()
        given(goodsService.updateGoods(any(), anyValue(GoodsUpdateRequest::class.java), any())).willReturn(response)

        mockMvc
            .perform(
                multipart("/api/goods/{goodsId}", 1L)
                    .file(requestPart)
                    .with { requestBuilder ->
                        requestBuilder.method = "PUT"
                        requestBuilder
                    },
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.name").value("수정 상품"))
            .andExpect(jsonPath("$.price").value(20_000))

        verify(goodsService).updateGoods(any(), anyValue(GoodsUpdateRequest::class.java), any())
    }

    @Test
    @DisplayName("상품 상태가 null이면 400 응답을 반환한다")
    fun updateGoodsStatusWithNullStatus() {
        val request = GoodsStatusUpdateRequest(null)

        mockMvc
            .perform(
                patch("/api/goods/{goodsId}/status", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("COMMON_001"))
            .andExpect(jsonPath("$.fieldErrors.status").value("상태값은 필수입니다."))

        verify(goodsService, never()).updateGoodsStatus(any(), anyValue(GoodsStatusUpdateRequest::class.java))
    }

    private fun jsonPart(
        name: String,
        request: Any,
    ): MockMultipartFile =
        MockMultipartFile(
            name,
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(request),
        )
}
