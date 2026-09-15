package com.team3.gudit.user.dto

import com.team3.gudit.user.domain.entity.Role
import com.team3.gudit.user.domain.entity.User

data class UserMeResponseDto(
    val id: Long?,
    val nickname: String?,
    val email: String?,
    val role: Role,
) {
    companion object {
        @JvmStatic
        fun from(user: User): UserMeResponseDto = UserMeResponseDto(
            id = user.id,
            nickname = user.nickname,
            email = user.email,
            role = user.role,
        )
    }
}
