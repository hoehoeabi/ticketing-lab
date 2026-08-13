package com.ticketing.ticketing_lab.domain.user.v1.controller;

import com.ticketing.ticketing_lab.domain.user.v1.dto.TokenResponseDto;
import com.ticketing.ticketing_lab.domain.user.v1.service.AuthService;
import com.ticketing.ticketing_lab.global.common.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/reissue")
    public RsData<TokenResponseDto> reissue(
            @RequestHeader("Refresh-Token") String refreshToken) {

        // "Bearer " 프레임워크 텍스트가 있을 경우 제거
        if (refreshToken.startsWith("Bearer ")) {
            refreshToken = refreshToken.substring(7);
        }

        TokenResponseDto response = authService.reissue(refreshToken);
        return RsData.of(
                "200",
                "토큰이 정상적으로 재발급되었습니다.",
                response
        );
    }
}
