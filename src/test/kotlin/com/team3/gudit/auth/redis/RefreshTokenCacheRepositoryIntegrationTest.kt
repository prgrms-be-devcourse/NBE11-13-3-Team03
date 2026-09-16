package com.team3.gudit.auth.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

@SpringBootTest
class RefreshTokenCacheRepositoryIntegrationTest @Autowired constructor(private val refreshTokenCacheRepository: RefreshTokenCacheRepository) {

    @AfterEach
    fun tearDown() {
        refreshTokenCacheRepository.delete(USER_ID)
    }

    companion object {
        private const val USER_ID = 1L
    }
    @Test
    @DisplayName("Refresh Token hash를 Redis에 저장하고 조회할 수 있다")
    fun saveAndFind() {
        // given
        val tokenHash = "test-hash"

        // when
        refreshTokenCacheRepository.save(
                USER_ID,
                tokenHash,
                Duration.ofMinutes(10)
        )

        // then
        val result =
                refreshTokenCacheRepository.findByUserId(USER_ID)

        assertThat(result)
                .contains(tokenHash)
    }

    @Test
    @DisplayName("같은 userId로 저장하면 기존 캐시를 덮어쓴다")
    fun overwrite() {
        // given
        refreshTokenCacheRepository.save(
                USER_ID,
                "old-hash",
                Duration.ofMinutes(10)
        )

        // when
        refreshTokenCacheRepository.save(
                USER_ID,
                "new-hash",
                Duration.ofMinutes(10)
        )

        // then
        val result =
                refreshTokenCacheRepository.findByUserId(USER_ID)

        assertThat(result)
                .contains("new-hash")
    }

    @Test
    @DisplayName("Refresh Token 캐시를 삭제하면 조회되지 않는다")
    fun delete() {
        // given
        refreshTokenCacheRepository.save(
                USER_ID,
                "test-hash",
                Duration.ofMinutes(10)
        )

        // when
        refreshTokenCacheRepository.delete(USER_ID)

        // then
        assertThat(
                refreshTokenCacheRepository.findByUserId(USER_ID)
        ).isEmpty()
    }

    @Test
    @DisplayName("TTL이 만료되면 Refresh Token 캐시가 조회되지 않는다")
    fun expire(){
        // given
        refreshTokenCacheRepository.save(
                USER_ID,
                "test-hash",
                Duration.ofSeconds(1)
        )

        // when
        Thread.sleep(1500)

        // then
        assertThat(
                refreshTokenCacheRepository.findByUserId(USER_ID)
        ).isEmpty()
    }
}
