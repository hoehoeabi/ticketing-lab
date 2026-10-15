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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationService emailVerificationService;

    /**
     * 회원가입
     * - 이메일 인증(email_verified:{email})이 완료된 경우에만 가입 허용
     */
    @Transactional
    public UserResponseDto signUp(UserSignUpReqDto reqDto) {
        if (userRepository.existsByEmail(reqDto.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (!emailVerificationService.isVerified(reqDto.email())) {
            log.warn("[회원가입 거부] 이메일 미인증 - email: {}", reqDto.email());
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = User.builder()
                .email(reqDto.email())
                .password(passwordEncoder.encode(reqDto.password()))
                .provider(reqDto.provider() != null ? reqDto.provider() : "LOCAL")
                .role(Role.ROLE_USER)
                .build();

        User savedUser = userRepository.save(user);

        // 인증 완료 플래그 1회성 소모 (동일 인증으로 재가입 시도 방지)
        emailVerificationService.consumeVerification(reqDto.email());
        log.info("[회원가입 완료] userId: {}, email: {}", savedUser.getId(), savedUser.getEmail());

        return UserResponseDto.from(savedUser);
    }

    /**
     * 로그인 (JWT 발급 및 RefreshToken Redis 저장)
     */
    @Transactional
    public TokenResponseDto login(UserLoginReqDto reqDto) {
        User user = userRepository.findByEmail(reqDto.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(reqDto.password(), user.getPassword())) {
            log.warn("[로그인 실패] 비밀번호 불일치 - email: {}", reqDto.email());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        // Redis에 Refresh Token 저장 또는 갱신 (TTL 적용)
        RefreshToken tokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .ttl(jwtProvider.getRefreshTokenExpirationSec())
                .build();
        refreshTokenRepository.save(tokenEntity);

        log.info("[로그인 성공] userId: {}, email: {}", user.getId(), user.getEmail());

        return new TokenResponseDto(accessToken, refreshToken);
    }

    /**
     * 내 정보 조회
     */
    @Transactional(readOnly = true)
    public UserResponseDto getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return UserResponseDto.from(user);
    }
}
