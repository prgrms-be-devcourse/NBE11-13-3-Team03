package com.team3.gudit.auth.service

import com.team3.gudit.auth.domain.entity.RefreshToken
import com.team3.gudit.auth.domain.repository.RefreshTokenRepository
import com.team3.gudit.auth.exception.AuthErrorCode
import com.team3.gudit.auth.jwt.*
import com.team3.gudit.auth.redis.RefreshTokenCacheRepository
import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.user.domain.entity.User
import com.team3.gudit.user.domain.repository.UserRepository
import com.team3.gudit.user.exception.UserErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
class TokenService(
    private val tokenProvider: TokenProvider,
    private val jwtProperties: JwtProperties,
    private val userRepository: UserRepository,
    private val refreshTokenHasher: RefreshTokenHasher,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val refreshTokenCacheRepository: RefreshTokenCacheRepository,
) {
    @JvmRecord
    data class TokenPair(val accessToken: String, val refreshToken: String)

    @Transactional
    fun issueToken(user: User): TokenPair {
        val tokenPair = generateTokenPair(user)
        saveOrUpdateRefreshToken(user, tokenPair.refreshToken)
        return tokenPair
    }

    // 탈취 방지를 위해 재발급 시 Access Token과 Refresh Token을 모두 갱신한다.
    @Transactional
    fun reissueToken(refreshToken: String?): TokenPair {
        validateRefreshToken(refreshToken)
        val token = requireNotNull(refreshToken)
        val userId = tokenProvider.getUserId(token)
        validateStoredRefreshToken(userId, token)
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(UserErrorCode.USER_NOT_FOUND) }
        val tokenPair = generateTokenPair(user)
        saveOrUpdateRefreshToken(user, tokenPair.refreshToken)
        return tokenPair
    }

    fun generateTokenPair(user: User): TokenPair = TokenPair(
        tokenProvider.generateToken(user, jwtProperties.accessTokenValidity, TokenType.ACCESS),
        tokenProvider.generateToken(user, jwtProperties.refreshTokenValidity, TokenType.REFRESH),
    )

    private fun saveOrUpdateRefreshToken(user: User, refreshToken: String) {
        val now = LocalDateTime.now()
        val tokenHash = refreshTokenHasher.hash(refreshToken)
        val expiresAt = now.plus(jwtProperties.refreshTokenValidity)
        val existingToken = refreshTokenRepository.findByUserId(user.id)
        if (existingToken.isPresent) {
            existingToken.get().rotate(tokenHash, expiresAt)
        } else {
            val newToken = RefreshToken(user, tokenHash, expiresAt)
            refreshTokenRepository.save(newToken)
        }
        refreshTokenCacheRepository.save(user.id, tokenHash, Duration.between(now, expiresAt))
    }

    private fun validateRefreshToken(refreshToken: String?) {
        if (refreshToken.isNullOrBlank()) {
            throw BusinessException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND)
        }
        val status = tokenProvider.validateToken(refreshToken, TokenType.REFRESH)
        if (status == TokenStatus.EXPIRED) {
            throw BusinessException(AuthErrorCode.EXPIRED_REFRESH_TOKEN)
        }
        if (status != TokenStatus.VALID) {
            throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)
        }
    }

    private fun validateStoredRefreshToken(userId: Long, refreshToken: String) {
        val cachedHash = refreshTokenCacheRepository.findByUserId(userId)
        if (cachedHash.isPresent && refreshTokenHasher.matches(cachedHash.get(), refreshToken)) {
            return
        }
        val storedToken = refreshTokenRepository.findByUserId(userId)
            .orElseThrow { BusinessException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND) }
        val now = LocalDateTime.now()
        if (!storedToken.expiresAt.isAfter(now)) {
            refreshTokenRepository.delete(storedToken)
            throw BusinessException(AuthErrorCode.EXPIRED_REFRESH_TOKEN)
        }
        if (!refreshTokenHasher.matches(storedToken.tokenHash, refreshToken)) {
            throw BusinessException(AuthErrorCode.REFRESH_TOKEN_MISMATCH)
        }
        val ttl = Duration.between(now, storedToken.expiresAt)
        refreshTokenCacheRepository.save(userId, storedToken.tokenHash, ttl)
    }
}
