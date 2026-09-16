package com.team3.gudit.auth.domain.entity

import com.team3.gudit.user.domain.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "refresh_tokens")
open class RefreshToken(
    user: User,
    tokenHash: String,
    expiresAt: LocalDateTime,
    id: Long? = null,
    createdAt: LocalDateTime? = null,
    updatedAt: LocalDateTime? = null,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = id
        protected set

    @field:OneToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(
        name = "user_id", nullable = false, unique = true,
        foreignKey = ForeignKey(name = "fk_refresh_tokens_user"),
    )
    open var user: User = user
        protected set

    @field:Column(name = "token_hash", unique = true, nullable = false)
    open var tokenHash: String = tokenHash
        protected set

    @field:Column(name = "expires_at", nullable = false)
    open var expiresAt: LocalDateTime = expiresAt
        protected set

    @field:Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime? = createdAt
        protected set

    @field:Column(name = "updated_at", nullable = false)
    open var updatedAt: LocalDateTime? = updatedAt
        protected set

    @PrePersist
    protected open fun onCreate() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    protected open fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }

    open fun rotate(tokenHash: String, expiresAt: LocalDateTime) {
        this.tokenHash = tokenHash
        this.expiresAt = expiresAt
    }

    // Java 테스트가 Kotlin으로 전환될 때까지 기존 빌더 호출을 지원한다.
    class Builder {
        private var user: User? = null
        private var tokenHash: String? = null
        private var expiresAt: LocalDateTime? = null

        fun user(user: User): Builder = apply { this.user = user }
        fun tokenHash(tokenHash: String): Builder = apply { this.tokenHash = tokenHash }
        fun expiresAt(expiresAt: LocalDateTime): Builder = apply { this.expiresAt = expiresAt }
        fun build(): RefreshToken = RefreshToken(requireNotNull(user), requireNotNull(tokenHash), requireNotNull(expiresAt))
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
