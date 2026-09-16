package com.team3.gudit.auth.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

@SpringBootTest
class RefreshTokenCacheRepositoryTest @Autowired constructor(private val refreshTokenCacheRepository: RefreshTokenCacheRepository) {
    @Test
    @DisplayName("Refresh Token hash를 Redis에 저장하고 조회할 수 있다.")
    fun saveAndFind() {
        // given
        val userId = 1L
        val tokenHash = "hash-value"

        // when
        refreshTokenCacheRepository.save(
                userId,
                tokenHash,
                Duration.ofMinutes(10)
        )

        // then
        val result = refreshTokenCacheRepository
                .findByUserId(userId)
                .orElseThrow()

        assertThat(result).isEqualTo(tokenHash)
    }

    @Test
    @DisplayName("같은 userId로 저장하면 기존 Refresh Token hash를 덮어쓴다.")
    fun overwrite() {
        // given
        val userId = 1L

        refreshTokenCacheRepository.save(
                userId,
                "old-hash",
                Duration.ofMinutes(10)
        )

        // when
        refreshTokenCacheRepository.save(
                userId,
                "new-hash",
                Duration.ofMinutes(10)
        )

        // then
        val result = refreshTokenCacheRepository
                .findByUserId(userId)
                .orElseThrow()

        assertThat(result).isEqualTo("new-hash")
    }

    @Test
    @DisplayName("Refresh Token 캐시를 삭제하면 더 이상 조회되지 않는다.")
    fun delete() {
        // given
        val userId = 1L

        refreshTokenCacheRepository.save(
                userId,
                "hash-value",
                Duration.ofMinutes(10)
        )

        // when
        refreshTokenCacheRepository.delete(userId)

        // then
        assertThat(
                refreshTokenCacheRepository.findByUserId(userId)
        ).isEmpty()
    }
}
