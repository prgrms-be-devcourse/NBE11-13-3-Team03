package com.team3.gudit.purchase.controller

import com.team3.gudit.auth.jwt.TokenProvider
import com.team3.gudit.auth.security.CustomUserDetails
import com.team3.gudit.purchase.dto.PurchaseCancelResponse
import com.team3.gudit.purchase.dto.PurchaseCreateResponse
import com.team3.gudit.purchase.dto.PurchaseDetailResponse
import com.team3.gudit.purchase.dto.PurchaseListResponse
import com.team3.gudit.purchase.entity.PurchaseStatus
import com.team3.gudit.purchase.service.PurchaseService
import java.time.LocalDateTime
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(PurchaseController::class)
class PurchaseControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var purchaseService: PurchaseService

    @MockitoBean
    private lateinit var tokenProvider: TokenProvider

    @Test
    @DisplayName("로그인 사용자가 판매 상품 구매를 요청한다")
    fun purchase() {
        // given
        val userId = 1L
        val saleId = 10L

        val userDetails = mock(CustomUserDetails::class.java)
        given(userDetails.userId).willReturn(userId)

        val response = PurchaseCreateResponse(
            100L,
            saleId,
            1,
            15000,
            PurchaseStatus.PENDING_PAYMENT,
            null,
            LocalDateTime.now(),
            "GUDIT_test-order-id"
        )

        given(purchaseService.purchase(userId, saleId))
            .willReturn(response)

        // when & then
        mockMvc.perform(post("/api/sales/{saleId}/purchases", saleId)
                .with(user(userDetails))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saleId").value(saleId))
            .andExpect(jsonPath("$.quantity").value(1))
            .andExpect(jsonPath("$.purchasePrice").value(15000))
            .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.orderId").value("GUDIT_test-order-id"))

        verify(purchaseService).purchase(userId, saleId)
    }

    @Test
    @DisplayName("로그인 사용자의 구매 목록을 조회한다")
    fun getMyPurchases() {
        // given
        val userId = 1L

        val userDetails = mock(CustomUserDetails::class.java)
        given(userDetails.userId).willReturn(userId)

        val response = PurchaseListResponse(
            listOf()
        )

        given(purchaseService.getMyPurchases(userId))
            .willReturn(response)

        // when & then
        mockMvc.perform(get("/api/purchases")
                .with(user(userDetails)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.purchases").isArray())

        verify(purchaseService).getMyPurchases(userId)
    }

    @Test
    @DisplayName("로그인 사용자가 자신의 구매 상세 내역을 조회한다")
    fun getPurchase() {
        // given
        val userId = 1L
        val purchaseId = 100L
        val saleId = 10L

        val userDetails = mock(CustomUserDetails::class.java)
        given(userDetails.userId).willReturn(userId)

        val response = PurchaseDetailResponse(
            purchaseId,
            saleId,
            20L,
            "테스트 굿즈",
            "https://example.com/image.jpg",
            1,
            15000,
            PurchaseStatus.PURCHASED,
            LocalDateTime.now(),
            null
        )

        given(purchaseService.getPurchase(userId, purchaseId))
            .willReturn(response)

        // when & then
        mockMvc.perform(get("/api/purchases/{purchaseId}", purchaseId)
                .with(user(userDetails)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.purchaseId").value(purchaseId))
            .andExpect(jsonPath("$.saleId").value(saleId))
            .andExpect(jsonPath("$.goodsName").value("테스트 굿즈"))
            .andExpect(jsonPath("$.purchasePrice").value(15000))
            .andExpect(jsonPath("$.status").value("PURCHASED"))

        verify(purchaseService).getPurchase(userId, purchaseId)
    }

    @Test
    @DisplayName("로그인 사용자가 자신의 구매를 취소한다")
    fun cancel() {
        // given
        val userId = 1L
        val purchaseId = 100L

        val userDetails = mock(CustomUserDetails::class.java)
        given(userDetails.userId).willReturn(userId)

        val response = PurchaseCancelResponse(
            purchaseId,
            PurchaseStatus.CANCELED,
            LocalDateTime.now()
        )

        given(purchaseService.cancel(userId, purchaseId))
            .willReturn(response)

        // when & then
        mockMvc.perform(post("/api/purchases/{purchaseId}/cancel", purchaseId)
                .with(user(userDetails))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.purchaseId").value(purchaseId))
            .andExpect(jsonPath("$.status").value("CANCELED"))
            .andExpect(jsonPath("$.canceledAt").isNotEmpty())

        verify(purchaseService).cancel(userId, purchaseId)
    }
}
