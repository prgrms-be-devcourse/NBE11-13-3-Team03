package com.team3.gudit.auth.service

import com.team3.gudit.auth.domain.repository.RefreshTokenRepository
import com.team3.gudit.auth.redis.RefreshTokenCacheRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val refreshTokenCacheRepository: RefreshTokenCacheRepository,
) {
    @Transactional
    fun logout(userId: Long) {
        refreshTokenRepository.deleteByUserId(userId)
        refreshTokenCacheRepository.delete(userId)
    }
}
