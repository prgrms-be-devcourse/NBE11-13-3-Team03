package com.team3.gudit.user.service;

import com.team3.gudit.global.exception.BusinessException;
import com.team3.gudit.user.domain.entity.Role;
import com.team3.gudit.user.domain.entity.User;
import com.team3.gudit.user.domain.repository.UserRepository;
import com.team3.gudit.user.dto.UserMeResponseDto;
import com.team3.gudit.user.exception.UserErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {

        user = new User(null, null, "테스트유저", "test@test.com", Role.USER, com.team3.gudit.auth.oauth2.AuthProvider.KAKAO, null, null);

        ReflectionTestUtils.setField(
                user,
                "id",
                1L
        );
    }

    @Test
    @DisplayName("사용자 정보를 조회한다")
    void getMyInfo_success() {

        // given
        Long userId = 1L;

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        UserMeResponseDto result =
                userService.getMyInfo(userId);

        // then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getNickname()).isEqualTo("테스트유저");
        assertThat(result.getEmail()).isEqualTo("test@test.com");
        assertThat(result.getRole()).isEqualTo(Role.USER);

        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("존재하지 않는 사용자를 조회하면 USER_NOT_FOUND 예외가 발생한다")
    void getMyInfo_userNotFound() {

        // given
        Long userId = 999L;

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> userService.getMyInfo(userId)
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {

                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(
                            businessException.getErrorCode()
                    ).isEqualTo(
                            UserErrorCode.USER_NOT_FOUND
                    );
                });

        verify(userRepository).findById(userId);
    }
}