package com.team3.gudit.auth.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "jwt")
class JwtProperties {
    // Spring이 Bean 초기화 시 필수 설정을 바인딩한다.
    lateinit var issuer: String
    lateinit var secretKey: String
    lateinit var accessTokenValidity: Duration
    lateinit var refreshTokenValidity: Duration
}
