package com.team3.gudit.global.exception

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "Error Code",
    description = "도메인별 업무 오류 코드 조회 API",
)
@RestController
@RequestMapping("/api/error-codes")
class ErrorCodeController(
    private val errorCodeProviders: List<ErrorCodeProvider<*>>,
) {
    @GetMapping
    @Operation(
        summary = "에러 코드 목록 조회",
        description = "전체 도메인의 업무 오류 코드와 HTTP 상태, 메시지를 도메인별로 조회합니다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "에러 코드 목록 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
    )
    @SecurityRequirement(name = "cookieAuth")
    fun getErrorCodes(): Map<String, List<ErrorCode>> {
        val errorCodesByDomain = HashMap<String, List<ErrorCode>>()

        errorCodeProviders.forEach { provider ->
            val domain = provider.getDomain()
            check(errorCodesByDomain.put(domain, provider.getErrorCodes().toList()) == null) {
                "Duplicate key $domain"
            }
        }

        return errorCodesByDomain
    }
}
