package com.team3.gudit.auth.jwt

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.HexFormat

@Component
class RefreshTokenHasher {
    fun hash(refreshToken: String): String {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            return HexFormat.of().formatHex(digest.digest(refreshToken.toByteArray(StandardCharsets.UTF_8)))
        } catch (exception: NoSuchAlgorithmException) {
            throw IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception)
        }
    }

    fun matches(storedHash: String, rawToken: String): Boolean = MessageDigest.isEqual(
        storedHash.toByteArray(StandardCharsets.UTF_8),
        hash(rawToken).toByteArray(StandardCharsets.UTF_8),
    )
}
