package com.team3.gudit.auth.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AccessTokenResponseDto(
    val accessToken: String? = null,
    val tokenType: String? = null,
    val expiresIn: Long? = null,
)
