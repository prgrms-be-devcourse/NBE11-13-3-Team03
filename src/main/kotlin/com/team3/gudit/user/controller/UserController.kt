package com.team3.gudit.user.controller

import com.team3.gudit.global.exception.ErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import com.team3.gudit.auth.security.CustomUserDetails
import com.team3.gudit.user.dto.UserMeResponseDto
import com.team3.gudit.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User", description = "사용자 정보 조회 API")
@RestController
@RequestMapping("api/users")
class UserController(
    private val userService: UserService,
) {
    @Operation(
        summary = "내 정보 조회",
        description = "현재 로그인한 사용자의 정보를 조회합니다.\n\nAccess Token을 통해 인증된 사용자의 정보를 반환합니다.\n",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "내 정보 조회 성공"),
            ApiResponse(responseCode = "401", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "Access Token이 없거나 유효하지 않은 경우"),
            ApiResponse(responseCode = "404", content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))], description = "사용자 정보를 찾을 수 없는 경우"),
        ],
    )
    @SecurityRequirement(name = "cookieAuth")
    @GetMapping("/me")
    fun getMyInfo(@AuthenticationPrincipal userDetails: CustomUserDetails): ResponseEntity<UserMeResponseDto> =
        ResponseEntity.ok(userService.getMyInfo(userDetails.userId))
}
