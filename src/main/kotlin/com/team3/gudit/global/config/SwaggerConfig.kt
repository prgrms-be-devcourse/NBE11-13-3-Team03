package com.team3.gudit.global.config

import io.swagger.v3.oas.models.Components
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

        return OpenAPI()
            .components(
                Components()
                    .addSecuritySchemes("cookieAuth", cookieAuth),
            )
    }
}
