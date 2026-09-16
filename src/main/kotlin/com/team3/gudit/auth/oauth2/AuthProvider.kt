package com.team3.gudit.auth.oauth2

import java.util.Locale

// 현재는 카카오만 있으나 확장을 위해 추가
enum class AuthProvider {
    KAKAO;

    companion object {
        @JvmStatic
        fun from(registrationId: String): AuthProvider =
            valueOf(registrationId.uppercase(Locale.getDefault()))
    }
}
