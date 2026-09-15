package com.team3.gudit.user.domain.entity

import com.team3.gudit.auth.oauth2.AuthProvider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "users")
open class User(
    id: Long? = null,
    kakaoId: Long? = null,
    nickname: String? = null,
    email: String? = null,
    role: Role,
    provider: AuthProvider = AuthProvider.KAKAO,
    createdAt: LocalDateTime? = null,
    updatedAt: LocalDateTime? = null,
) {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = id
        protected set

    @field:Column(name = "kakao_id", unique = true)
    open var kakaoId: Long? = kakaoId
        protected set

    @field:Column(name = "nickname")
    open var nickname: String? = nickname
        protected set

    @field:Column(name = "email")
    open var email: String? = email
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "role", nullable = false, length = 20)
    open var role: Role = role
        protected set

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "provider", nullable = false, length = 20)
    open var provider: AuthProvider = provider
        protected set

    @field:Column(name = "created_at", nullable = false)
    open var createdAt: LocalDateTime? = createdAt
        protected set

    @field:Column(name = "updated_at", nullable = false)
    open var updatedAt: LocalDateTime? = updatedAt
        protected set

    open fun updateProfile(nickname: String?): User {
        this.nickname = nickname
        return this
    }

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

    open fun promoteToAdmin() {
        role = Role.ADMIN
    }
}
