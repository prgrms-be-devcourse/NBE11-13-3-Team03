package com.team3.gudit.auth.service

import com.team3.gudit.auth.domain.entity.RefreshToken
import com.team3.gudit.auth.domain.repository.RefreshTokenRepository
import com.team3.gudit.auth.exception.AuthErrorCode
import com.team3.gudit.auth.jwt.*
import com.team3.gudit.auth.redis.RefreshTokenCacheRepository
import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.user.domain.entity.Role
import com.team3.gudit.user.domain.entity.User
import com.team3.gudit.user.domain.repository.UserRepository
import com.team3.gudit.user.exception.UserErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.time.Duration
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class TokenServiceTest(
    @param:Mock private val tokenProvider: TokenProvider,
    @param:Mock private val jwtProperties: JwtProperties,
    @param:Mock private val userRepository: UserRepository,
    @param:Mock private val refreshTokenHasher: RefreshTokenHasher,
    @param:Mock private val refreshTokenRepository: RefreshTokenRepository,
    @param:Mock private val refreshTokenCacheRepository: RefreshTokenCacheRepository,
) {
    private val tokenService = TokenService(tokenProvider, jwtProperties, userRepository, refreshTokenHasher, refreshTokenRepository, refreshTokenCacheRepository)
    private val user = User.builder().nickname("testUser").role(Role.USER).build()

    @BeforeEach
    fun setUp() {
        ReflectionTestUtils.setField(user, "id", USER_ID)
    }

    @Test
    @DisplayName("토큰 발급 시 Access Token과 Refresh Token을 발급하고 Refresh Token을 저장한다")
    fun issueToken_success_newReissueToken() {
        // given
        `when`(jwtProperties.accessTokenValidity).thenReturn(ACCESS_VALIDITY)
        `when`(jwtProperties.refreshTokenValidity).thenReturn(REFRESH_VALIDITY)
        `when`(tokenProvider.generateToken(user, ACCESS_VALIDITY, TokenType.ACCESS)).thenReturn(ACCESS_TOKEN)
        `when`(tokenProvider.generateToken(user, REFRESH_VALIDITY, TokenType.REFRESH)).thenReturn(REFRESH_TOKEN)
        `when`(refreshTokenHasher.hash(REFRESH_TOKEN)).thenReturn(STORED_HASH)
        `when`(refreshTokenRepository.findByUserId(USER_ID)).thenReturn(Optional.empty())
        // Spring Data save의 non-null 반환 계약을 실제 저장소와 동일하게 설정한다.
        doAnswer { invocation -> invocation.getArgument<RefreshToken>(0) }
            .`when`(refreshTokenRepository)
            .save(any(RefreshToken::class.java) ?: RefreshToken(user, STORED_HASH, LocalDateTime.now()))

        // when
        val result = tokenService.issueToken(user)

        // then
        assertThat(result.accessToken).isEqualTo(ACCESS_TOKEN)
        assertThat(result.refreshToken).isEqualTo(REFRESH_TOKEN)
        val captor = ArgumentCaptor.forClass(RefreshToken::class.java)
        verify(refreshTokenRepository).save(captor.capture() ?: RefreshToken(user, "", LocalDateTime.now()))
        val savedToken = captor.value
        assertThat(savedToken.user).isEqualTo(user)
        assertThat(savedToken.tokenHash).isEqualTo(STORED_HASH)
        assertThat(savedToken.expiresAt).isNotNull()
    }

    @Test
    @DisplayName("기존 Refresh Token이 있으면 새로운 Refresh Token으로 갱신한다")
    fun issueToken_success_existingReissueToken() {
        // given
        val storedToken = RefreshToken(user, "old-hash", LocalDateTime.now().plusDays(1))
        ReflectionTestUtils.setField(storedToken, "id", 1L)
        `when`(jwtProperties.accessTokenValidity).thenReturn(ACCESS_VALIDITY)
        `when`(jwtProperties.refreshTokenValidity).thenReturn(REFRESH_VALIDITY)
        `when`(tokenProvider.generateToken(user, ACCESS_VALIDITY, TokenType.ACCESS)).thenReturn(ACCESS_TOKEN)
        `when`(tokenProvider.generateToken(user, REFRESH_VALIDITY, TokenType.REFRESH)).thenReturn(REFRESH_TOKEN)
        `when`(refreshTokenHasher.hash(REFRESH_TOKEN)).thenReturn(STORED_HASH)
        `when`(refreshTokenRepository.findByUserId(USER_ID)).thenReturn(Optional.of(storedToken))

        // when
        val result = tokenService.issueToken(user)

        // then
        assertThat(result.accessToken).isEqualTo(ACCESS_TOKEN)
        assertThat(result.refreshToken).isEqualTo(REFRESH_TOKEN)
        assertThat(storedToken.tokenHash).isEqualTo(STORED_HASH)
        verify(refreshTokenRepository, never()).save(any(RefreshToken::class.java) ?: storedToken)
    }

    @Test
    @DisplayName("유효한 Refresh Token이면 새로운 Access Token과 Refresh Token을 발급한다")
    fun reissueToken_success() {
        // given
        val storedToken = RefreshToken(user, STORED_HASH, LocalDateTime.now().plusDays(14))
        `when`(tokenProvider.validateToken(REFRESH_TOKEN, TokenType.REFRESH)).thenReturn(TokenStatus.VALID)
        `when`(tokenProvider.getUserId(REFRESH_TOKEN)).thenReturn(USER_ID)
        // Redis Cache Miss -> DB 조회
        `when`(refreshTokenCacheRepository.findByUserId(USER_ID)).thenReturn(Optional.empty())
        `when`(refreshTokenRepository.findByUserId(USER_ID)).thenReturn(Optional.of(storedToken))
        `when`(refreshTokenHasher.matches(STORED_HASH, REFRESH_TOKEN)).thenReturn(true)
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.of(user))
        `when`(jwtProperties.accessTokenValidity).thenReturn(ACCESS_VALIDITY)
        `when`(jwtProperties.refreshTokenValidity).thenReturn(REFRESH_VALIDITY)
        `when`(tokenProvider.generateToken(user, ACCESS_VALIDITY, TokenType.ACCESS)).thenReturn(NEW_ACCESS_TOKEN)
        `when`(tokenProvider.generateToken(user, REFRESH_VALIDITY, TokenType.REFRESH)).thenReturn(NEW_REFRESH_TOKEN)
        `when`(refreshTokenHasher.hash(NEW_REFRESH_TOKEN)).thenReturn(NEW_HASH)

        val cacheTtlCaptor = ArgumentCaptor.forClass(Duration::class.java)

        // when
        val result = tokenService.reissueToken(REFRESH_TOKEN)

        // then
        assertThat(result.accessToken).isEqualTo(NEW_ACCESS_TOKEN)
        assertThat(result.refreshToken).isEqualTo(NEW_REFRESH_TOKEN)
        assertThat(storedToken.tokenHash).isEqualTo(NEW_HASH)
        // 캐시 미스 후 DB의 유효한 토큰으로 캐시를 복구할 때 TTL은 남은 만료 시간이다.
        verify(refreshTokenCacheRepository).save(
            eq(USER_ID), eq(STORED_HASH) ?: STORED_HASH,
            cacheTtlCaptor.capture() ?: Duration.ZERO,
        )
        assertThat(cacheTtlCaptor.value).isPositive().isLessThanOrEqualTo(REFRESH_VALIDITY)
        verify(tokenProvider, times(1)).generateToken(user, ACCESS_VALIDITY, TokenType.ACCESS)
        verify(tokenProvider, times(1)).generateToken(user, REFRESH_VALIDITY, TokenType.REFRESH)
        // Mockito 매처의 null 반환값에 기본값을 제공해 Kotlin 호출의 null 검사를 통과한다.
        verify(refreshTokenCacheRepository).save(eq(USER_ID), eq(NEW_HASH) ?: NEW_HASH, any(Duration::class.java) ?: Duration.ZERO)
    }

    @Test
    @DisplayName("Refresh Token이 null이면 REFRESH_TOKEN_NOT_FOUND 예외가 발생한다")
    fun reissueToken_fail_nullToken() {
        val exception = assertThrows(BusinessException::class.java) { tokenService.reissueToken(null) }
        assertThat(exception.errorCode).isEqualTo(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND)
        verifyNoInteractions(tokenProvider, refreshTokenRepository, userRepository)
    }

    @Test
    @DisplayName("Refresh Token이 빈 문자열이면 REFRESH_TOKEN_NOT_FOUND 예외가 발생한다")
    fun reissueToken_fail_blankToken() {
        val exception = assertThrows(BusinessException::class.java) { tokenService.reissueToken(" ") }
        assertThat(exception.errorCode).isEqualTo(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND)
        verifyNoInteractions(tokenProvider, refreshTokenRepository, userRepository)
    }

    @Test
    @DisplayName("만료된 Refresh Token이면 EXPIRED_REFRESH_TOKEN 예외가 발생한다")
    fun reissueToken_fail_expiredToken() {
        `when`(tokenProvider.validateToken(REFRESH_TOKEN, TokenType.REFRESH)).thenReturn(TokenStatus.EXPIRED)
        val exception = assertThrows(BusinessException::class.java) { tokenService.reissueToken(REFRESH_TOKEN) }
        assertThat(exception.errorCode).isEqualTo(AuthErrorCode.EXPIRED_REFRESH_TOKEN)
        verify(tokenProvider, never()).getUserId(anyString())
        verifyNoInteractions(refreshTokenRepository, userRepository)
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token이면 INVALID_REFRESH_TOKEN 예외가 발생한다")
    fun reissueToken_fail_invalidToken() {
        `when`(tokenProvider.validateToken(REFRESH_TOKEN, TokenType.REFRESH)).thenReturn(TokenStatus.INVALID)
        val exception = assertThrows(BusinessException::class.java) { tokenService.reissueToken(REFRESH_TOKEN) }
        assertThat(exception.errorCode).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN)
        verify(tokenProvider, never()).getUserId(anyString())
        verifyNoInteractions(refreshTokenRepository, userRepository)
    }

    @Test
    @DisplayName("DB에 Refresh Token이 없으면 REFRESH_TOKEN_NOT_FOUND 예외가 발생한다")
    fun reissueToken_fail_storedTokenNotFound() {
        `when`(tokenProvider.validateToken(REFRESH_TOKEN, TokenType.REFRESH)).thenReturn(TokenStatus.VALID)
        `when`(tokenProvider.getUserId(REFRESH_TOKEN)).thenReturn(USER_ID)
        `when`(refreshTokenRepository.findByUserId(USER_ID)).thenReturn(Optional.empty())
        val exception = assertThrows(BusinessException::class.java) { tokenService.reissueToken(REFRESH_TOKEN) }
        assertThat(exception.errorCode).isEqualTo(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND)
        verifyNoInteractions(userRepository)
    }

    @Test
    @DisplayName("요청 Refresh Token과 DB의 Refresh Token이 일치하지 않으면 REFRESH_TOKEN_MISMATCH 예외가 발생한다")
    fun reissueToken_fail_tokenMismatch() {
        val storedToken = RefreshToken(user, STORED_HASH, LocalDateTime.now().plusDays(14))
        `when`(tokenProvider.validateToken(REFRESH_TOKEN, TokenType.REFRESH)).thenReturn(TokenStatus.VALID)
        `when`(tokenProvider.getUserId(REFRESH_TOKEN)).thenReturn(USER_ID)
        `when`(refreshTokenRepository.findByUserId(USER_ID)).thenReturn(Optional.of(storedToken))
        `when`(refreshTokenHasher.matches(STORED_HASH, REFRESH_TOKEN)).thenReturn(false)
        val exception = assertThrows(BusinessException::class.java) { tokenService.reissueToken(REFRESH_TOKEN) }
        assertThat(exception.errorCode).isEqualTo(AuthErrorCode.REFRESH_TOKEN_MISMATCH)
        verifyNoInteractions(userRepository)
    }

    @Test
    @DisplayName("Refresh Token의 사용자가 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다")
    fun reissueToken_fail_userNotFound() {
        val storedToken = RefreshToken(user, STORED_HASH, LocalDateTime.now().plusDays(14))
        `when`(tokenProvider.validateToken(REFRESH_TOKEN, TokenType.REFRESH)).thenReturn(TokenStatus.VALID)
        `when`(tokenProvider.getUserId(REFRESH_TOKEN)).thenReturn(USER_ID)
        `when`(refreshTokenRepository.findByUserId(USER_ID)).thenReturn(Optional.of(storedToken))
        `when`(refreshTokenHasher.matches(STORED_HASH, REFRESH_TOKEN)).thenReturn(true)
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.empty())
        val exception = assertThrows(BusinessException::class.java) { tokenService.reissueToken(REFRESH_TOKEN) }
        assertThat(exception.errorCode).isEqualTo(UserErrorCode.USER_NOT_FOUND)
        verify(tokenProvider, never()).generateToken(
            any(User::class.java) ?: user,
            any(Duration::class.java) ?: Duration.ZERO,
            any(TokenType::class.java) ?: TokenType.ACCESS,
        )
    }

    companion object {
        private const val USER_ID = 1L
        private const val ACCESS_TOKEN = "access-token"
        private const val REFRESH_TOKEN = "refresh-token"
        private const val NEW_ACCESS_TOKEN = "new-access-token"
        private const val NEW_REFRESH_TOKEN = "new-refresh-token"
        private const val STORED_HASH = "stored-refresh-token-hash"
        private const val NEW_HASH = "new-refresh-token-hash"
        private val ACCESS_VALIDITY = Duration.ofMinutes(30)
        private val REFRESH_VALIDITY = Duration.ofDays(14)
    }
}
