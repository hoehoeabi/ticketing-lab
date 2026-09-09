package com.ticketing.ticketing_lab.domain.user.v1.service;

import com.ticketing.ticketing_lab.domain.user.entity.User;
import com.ticketing.ticketing_lab.domain.user.enums.Role;
import com.ticketing.ticketing_lab.domain.user.repository.UserRepository;
import com.ticketing.ticketing_lab.domain.user.v1.dto.TokenResponseDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.UserLoginReqDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.UserResponseDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.UserSignUpReqDto;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import com.ticketing.ticketing_lab.global.security.jwt.JwtProvider;
import com.ticketing.ticketing_lab.global.security.jwt.RefreshToken;
import com.ticketing.ticketing_lab.global.security.jwt.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("회원가입 성공 - 비밀번호가 암호화되어 저장된다")
    void signUpSuccess() {
        // given
        UserSignUpReqDto reqDto = new UserSignUpReqDto("test@test.com", "password123", "LOCAL");
        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("encodedPassword");

        User savedUser = User.builder()
                .email("test@test.com")
                .password("encodedPassword")
                .provider("LOCAL")
                .role(Role.ROLE_USER)
                .build();
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        // when
        UserResponseDto response = userService.signUp(reqDto);

        // then
        assertThat(response.email()).isEqualTo("test@test.com");
        assertThat(response.role()).isEqualTo(Role.ROLE_USER);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이미 존재하는 이메일이면 DUPLICATE_EMAIL 예외가 발생한다")
    void signUpFailDuplicateEmail() {
        // given
        UserSignUpReqDto reqDto = new UserSignUpReqDto("duplicate@test.com", "password123", "LOCAL");
        given(userRepository.existsByEmail("duplicate@test.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.signUp(reqDto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("로그인 성공 - 비밀번호 일치 시 AccessToken 및 RefreshToken이 발급된다")
    void loginSuccess() {
        // given
        UserLoginReqDto reqDto = new UserLoginReqDto("test@test.com", "password123");
        User user = User.builder()
                .email("test@test.com")
                .password("encodedPassword")
                .role(Role.ROLE_USER)
                .build();

        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "encodedPassword")).willReturn(true);
        given(jwtProvider.createAccessToken(any(), any(), any())).willReturn("mockAccessToken");
        given(jwtProvider.createRefreshToken(any())).willReturn("mockRefreshToken");
        given(jwtProvider.getRefreshTokenExpirationSec()).willReturn(604800L);

        // when
        TokenResponseDto response = userService.login(reqDto);

        // then
        assertThat(response.accessToken()).isEqualTo("mockAccessToken");
        assertThat(response.refreshToken()).isEqualTo("mockRefreshToken");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호가 일치하지 않으면 LOGIN_FAILED 예외가 발생한다")
    void loginFailWrongPassword() {
        // given
        UserLoginReqDto reqDto = new UserLoginReqDto("test@test.com", "wrongPassword");
        User user = User.builder()
                .email("test@test.com")
                .password("encodedPassword")
                .role(Role.ROLE_USER)
                .build();

        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPassword", "encodedPassword")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.login(reqDto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("내 정보 조회 성공")
    void getUserProfileSuccess() {
        // given
        User user = User.builder()
                .email("profile@test.com")
                .role(Role.ROLE_USER)
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        UserResponseDto response = userService.getUserProfile(1L);

        // then
        assertThat(response.email()).isEqualTo("profile@test.com");
    }
}
