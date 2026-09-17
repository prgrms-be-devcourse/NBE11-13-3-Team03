package com.team3.gudit.auth.domain.repository

import com.team3.gudit.auth.domain.entity.RefreshToken
import com.team3.gudit.user.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByUserId(userId: Long): Optional<RefreshToken>
    fun deleteByUserId(userId: Long)
    fun user(user: User): Long
}
