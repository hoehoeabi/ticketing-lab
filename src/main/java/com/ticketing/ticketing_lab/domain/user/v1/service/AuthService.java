package com.ticketing.ticketing_lab.domain.user.v1.service;

import com.ticketing.ticketing_lab.domain.user.v1.dto.TokenResponseDto;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import com.ticketing.ticketing_lab.global.security.jwt.JwtProvider;
import com.ticketing.ticketing_lab.global.security.jwt.RefreshToken;
import com.ticketing.ticketing_lab.global.security.jwt.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * RTR 기반 토큰 재발급 (Reissue)
     */
    @Transactional
    public TokenResponseDto reissue(String refreshToken) {
        // Refresh Token 자체 서명 및 유효성 검증
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 토큰에서 User ID 추출
        Long userId = jwtProvider.getUserId(refreshToken);

        // Redis에서 해당 유저의 Refresh Token 조회
        RefreshToken savedToken = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Redis에 저장된 Refresh Token이 존재하지 않습니다. User ID: {}", userId);
                    return new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
                });

        // [RTR / 탈취 감지]
        // 클라이언트가 보낸 토큰과 Redis의 최신 토큰이 일치하지 않는 경우
        if (!savedToken.getToken().equals(refreshToken)) {
            log.error("🚨 [탈취 감지] 저장된 토큰과 불일치하는 옛날 Refresh Token으로 재발급을 시도했습니다! User ID: {}", userId);
            // 해당 유저의 토큰 무효화 (Redis에서 삭제)
            refreshTokenRepository.delete(savedToken);
            throw new BusinessException(ErrorCode.TOKEN_THEFT_DETECTED);
        }

        // 신규 Access Token & Refresh Token 생성
        String userEmail = jwtProvider.getClaims(refreshToken).get("email", String.class);
        String userRole = jwtProvider.getClaims(refreshToken).get("role", String.class);

        String newAccessToken = jwtProvider.createAccessToken(userId, userEmail, userRole);
        String newRefreshToken = jwtProvider.createRefreshToken(userId);

        // Redis의 Refresh Token 정보 갱신 (RTR)
        savedToken.updateToken(newRefreshToken, jwtProvider.getRefreshTokenExpirationSec());
        refreshTokenRepository.save(savedToken);

        log.info("성공적으로 토큰이 재발급(RTR)되었습니다. User ID: {}", userId);

        return new TokenResponseDto(newAccessToken, newRefreshToken);
    }
}
