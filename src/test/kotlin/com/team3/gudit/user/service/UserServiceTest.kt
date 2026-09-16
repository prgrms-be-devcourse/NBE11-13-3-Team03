package com.team3.gudit.user.service

import com.team3.gudit.global.exception.BusinessException
import com.team3.gudit.user.domain.entity.Role
import com.team3.gudit.user.domain.entity.User
import com.team3.gudit.user.domain.repository.UserRepository
import com.team3.gudit.user.exception.UserErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional
import java.util.function.Consumer

class UserServiceTest {
    private val userRepository = mock(UserRepository::class.java)
    private val userService = UserService(userRepository)
    private val user = User(
        id = 1L,
        nickname = "테스트유저",
        email = "test@test.com",
        role = Role.USER,
    )

    @Test
    @DisplayName("사용자 정보를 조회한다")
    fun getMyInfo_success() {
        // given
        val userId = 1L
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(user))

        // when
        val result = userService.getMyInfo(userId)

        // then
        assertThat(result.id).isEqualTo(1L)
        assertThat(result.nickname).isEqualTo("테스트유저")
        assertThat(result.email).isEqualTo("test@test.com")
        assertThat(result.role).isEqualTo(Role.USER)
        verify(userRepository).findById(userId)
    }

    @Test
    @DisplayName("존재하지 않는 사용자를 조회하면 USER_NOT_FOUND 예외가 발생한다")
    fun getMyInfo_userNotFound() {
        // given
        val userId = 999L
        `when`(userRepository.findById(userId)).thenReturn(Optional.empty())

        // when & then
        assertThatExceptionOfType(BusinessException::class.java)
            .isThrownBy { userService.getMyInfo(userId) }
            .satisfies(Consumer<BusinessException> { exception ->
                assertThat(exception.errorCode).isEqualTo(UserErrorCode.USER_NOT_FOUND)
            })
        verify(userRepository).findById(userId)
    }
}
