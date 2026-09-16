package com.team3.gudit.user.domain.repository

import com.team3.gudit.auth.oauth2.AuthProvider
import com.team3.gudit.user.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<User, Long> {
    // Keep Optional for the existing Java OAuth caller during incremental migration.
    fun findByKakaoIdAndProvider(kakaoId: Long, provider: AuthProvider): Optional<User>
}
