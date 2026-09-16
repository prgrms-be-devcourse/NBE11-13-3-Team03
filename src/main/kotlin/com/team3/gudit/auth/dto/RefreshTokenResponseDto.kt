package com.team3.gudit.auth.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RefreshTokenResponseDto(
    val validated: Boolean = false,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)
