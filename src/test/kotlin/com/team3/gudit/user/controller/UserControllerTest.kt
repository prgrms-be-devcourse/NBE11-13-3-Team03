package com.team3.gudit.user.controller

import com.team3.gudit.auth.security.CustomUserDetails
import com.team3.gudit.user.domain.entity.Role
import com.team3.gudit.user.dto.UserMeResponseDto
import com.team3.gudit.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus

class UserControllerTest {
    private val userService = mock(UserService::class.java)
    private val userController = UserController(userService)

    @Test
    @DisplayName("인증된 사용자의 정보를 조회한다")
    fun getMyInfo_success() {
        // given
        val userId = 1L
        val userDetails = CustomUserDetails(userId, Role.USER)
        val responseDto = UserMeResponseDto(userId, "테스트유저", "test@test.com", Role.USER)
        `when`(userService.getMyInfo(userId)).thenReturn(responseDto)

        // when
        val response = userController.getMyInfo(userDetails)

        // then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull()
        val body = requireNotNull(response.body)
        assertThat(body.id).isEqualTo(userId)
        assertThat(body.nickname).isEqualTo("테스트유저")
        assertThat(body.email).isEqualTo("test@test.com")
        assertThat(body.role).isEqualTo(Role.USER)
        verify(userService).getMyInfo(userId)
    }
}
