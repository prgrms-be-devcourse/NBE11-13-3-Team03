package com.team3.gudit.auth

import com.team3.gudit.auth.jwt.TokenProvider
import com.team3.gudit.auth.jwt.TokenType
import com.team3.gudit.user.domain.entity.Role
import com.team3.gudit.user.domain.entity.User
import com.team3.gudit.user.domain.repository.UserRepository
import com.team3.gudit.payment.dto.PaymentStatusResult
import com.team3.gudit.payment.entity.PaymentStatus
import com.team3.gudit.payment.service.PaymentService
import com.team3.gudit.purchase.entity.PurchaseStatus
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationIntegrationTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val mockMvc: MockMvc,
    private val tokenProvider: TokenProvider,
) {
    @MockitoBean
    private lateinit var paymentService: PaymentService

    private var user = User.builder()
        .kakaoId(12345L).role(Role.USER).nickname("testUser").email("test@example.com").build()

    @BeforeEach
    fun setUp() {
        user = userRepository.saveAndFlush(user)
    }

    @Test
    fun internalApiWithValidKeyDoesNotRequireAccessToken() {
        given(paymentService.getStatus("GUDIT_internal-test")).willReturn(
            PaymentStatusResult("GUDIT_internal-test", 100L, PurchaseStatus.PURCHASED, PaymentStatus.DONE, 15000)
        )
        mockMvc.perform(get("/api/internal/payments/GUDIT_internal-test/cs-status")
            .servletPath("/api/internal/payments/GUDIT_internal-test/cs-status")
            .header("X-INTERNAL-KEY", "test-internal-api-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value("GUDIT_internal-test"))
    }

    @Test
    fun internalApiWithoutKeyIsRejectedEvenWithValidAccessToken() {
        val token = tokenProvider.generateToken(user, Duration.ofMinutes(30), TokenType.ACCESS)
        mockMvc.perform(get("/api/internal/payments/GUDIT_internal-test/cs-status")
            .servletPath("/api/internal/payments/GUDIT_internal-test/cs-status")
            .cookie(Cookie("access_token", token)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_008"))
        verifyNoInteractions(paymentService)
    }

    @Test
    fun internalApiWithWrongKeyIsRejectedBeforeController() {
        mockMvc.perform(get("/api/internal/payments/GUDIT_internal-test/cs-status")
            .servletPath("/api/internal/payments/GUDIT_internal-test/cs-status")
            .header("X-INTERNAL-KEY", "wrong-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_008"))
        verifyNoInteractions(paymentService)
    }

    @Test
    fun internalKeyCannotAuthenticateNormalUserApi() {
        mockMvc.perform(get("/api/users/me").header("X-INTERNAL-KEY", "test-internal-api-key"))
            .andExpect(status().isUnauthorized())
    }

    @Test
    fun userAccessTokenCannotAccessAdminApi() {
        val token = tokenProvider.generateToken(user, Duration.ofMinutes(30), TokenType.ACCESS)
        mockMvc.perform(get("/api/goods").cookie(Cookie("access_token", token)))
            .andExpect(status().isForbidden())
    }
    @Test
    @DisplayName("정상 Access Token으로 보호 API에 접근하면 성공한다")
    fun authenticated_success(){

        // given
        val accessToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofMinutes(30),
                        TokenType.ACCESS
                )

        // when & then
        mockMvc.perform(
                        get("/api/users/me")
                                .cookie(
                                        Cookie(
                                                "access_token",
                                                accessToken
                                        )
                                )
                )
                .andExpect(status().isOk())
    }


    @Test
    @DisplayName("Access Token 없이 보호 API에 접근하면 401을 반환한다")
    fun unauthenticated_fail(){

        // when & then
        mockMvc.perform(
                        get("/api/users/me")
                )
                .andExpect(status().isUnauthorized())
    }


    @Test
    @DisplayName("만료된 Access Token으로 보호 API에 접근하면 401을 반환한다")
    fun expiredAccessToken_fail(){

        // given
        val expiredToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofSeconds(-1),
                        TokenType.ACCESS
                )

        // when & then
        mockMvc.perform(
                        get("/api/users/me")
                                .cookie(
                                        Cookie(
                                                "access_token",
                                                expiredToken
                                        )
                                )
                )
                .andExpect(status().isUnauthorized())
    }


    @Test
    @DisplayName("유효하지 않은 Access Token으로 보호 API에 접근하면 401을 반환한다")
    fun invalidAccessToken_fail(){

        // when & then
        mockMvc.perform(
                        get("/api/users/me")
                                .cookie(
                                        Cookie(
                                                "access_token",
                                                "invalid-token"
                                        )
                                )
                )
                .andExpect(status().isUnauthorized())
    }


    @Test
    @DisplayName("Refresh Token으로 보호 API에 접근하면 401을 반환한다")
    fun refreshTokenAsAccessToken_fail(){

        // given
        val refreshToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofDays(14),
                        TokenType.REFRESH
                )

        // when & then
        mockMvc.perform(
                        get("/api/users/me")
                                .cookie(
                                        Cookie(
                                                "access_token",
                                                refreshToken
                                        )
                                )
                )
                .andExpect(status().isUnauthorized())
    }
}
