package com.team3.gudit.auth.oauth2

interface OAuth2UserInfo {
    fun attributes(): Map<String, Any>
    fun id(): Long?
    fun email(): String?
    fun name(): String?
    fun imageUrl(): String?
}
