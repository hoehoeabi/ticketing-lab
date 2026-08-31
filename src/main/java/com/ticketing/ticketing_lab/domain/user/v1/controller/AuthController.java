package com.ticketing.ticketing_lab.domain.user.v1.controller;

import com.ticketing.ticketing_lab.domain.user.v1.dto.AccessTokenResponseDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.TokenResponseDto;
import com.ticketing.ticketing_lab.domain.user.v1.service.AuthService;
import com.ticketing.ticketing_lab.global.common.RsData;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/reissue")
    public RsData<AccessTokenResponseDto> reissue(
            @CookieValue("refreshToken") String refreshToken,
            HttpServletResponse response) {

        TokenResponseDto tokenResponse = authService.reissue(refreshToken);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", tokenResponse.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return RsData.of(
                "200",
                "토큰이 정상적으로 재발급되었습니다.",
                new AccessTokenResponseDto(tokenResponse.accessToken())
        );
    }
}
