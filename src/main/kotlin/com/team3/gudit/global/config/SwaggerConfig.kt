package com.team3.gudit.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {
    @Bean
    fun openAPI(): OpenAPI {
        val cookieAuth = SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .`in`(SecurityScheme.In.COOKIE)
            .name("access_token")
            .description("카카오 로그인 후 브라우저가 전송하는 HttpOnly 쿠키입니다. Swagger UI에서 쿠키 값을 직접 설정할 수 없으므로 같은 사이트에서 먼저 로그인하세요.")

        return OpenAPI()
            .info(Info().title("Gudit API").version("v1").description("카카오 로그인: /oauth2/authorization/kakao. 이메일 제공이 필수이며, 이메일이 누락되면 인증에 실패합니다. 토큰 재발급에는 refresh_token 쿠키가 필요합니다."))
            .components(
                Components()
                    .addSecuritySchemes("cookieAuth", cookieAuth)
                    .addSecuritySchemes("internalApiKey", SecurityScheme().type(SecurityScheme.Type.APIKEY).`in`(SecurityScheme.In.HEADER).name("X-INTERNAL-KEY")),
            )
    }
}
