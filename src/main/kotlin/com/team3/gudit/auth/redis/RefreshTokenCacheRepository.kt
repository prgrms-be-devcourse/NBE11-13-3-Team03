package com.team3.gudit.auth.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.Optional

@Repository
class RefreshTokenCacheRepository(private val redisTemplate: StringRedisTemplate) {
    fun save(userId: Long, tokenHash: String, ttl: Duration) {
        redisTemplate.opsForValue().set(PREFIX + userId, tokenHash, ttl)
    }

    fun findByUserId(userId: Long): Optional<String> =
        Optional.ofNullable(redisTemplate.opsForValue().get(PREFIX + userId))

    fun delete(userId: Long) {
        redisTemplate.delete(PREFIX + userId)
    }

    companion object {
        private const val PREFIX = "refresh-token:"
    }
}
