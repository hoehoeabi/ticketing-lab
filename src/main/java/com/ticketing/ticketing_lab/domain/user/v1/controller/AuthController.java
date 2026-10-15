package com.ticketing.ticketing_lab.domain.user.v1.controller;

import com.ticketing.ticketing_lab.domain.user.v1.dto.AccessTokenResponseDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.EmailSendReqDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.EmailSendResponseDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.EmailVerifyReqDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.TokenResponseDto;
import com.ticketing.ticketing_lab.domain.user.v1.service.AuthService;
import com.ticketing.ticketing_lab.domain.user.v1.service.EmailVerificationService;
import com.ticketing.ticketing_lab.global.common.RsData;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
    private final EmailVerificationService emailVerificationService;

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

    /**
     * 이메일 인증번호 발송 API
     * - 6자리 인증번호를 Redis에 저장(TTL 5분) 후 SMTP로 발송
     */
    @PostMapping("/email/send")
    public RsData<EmailSendResponseDto> sendEmailVerificationCode(@Valid @RequestBody EmailSendReqDto reqDto) {
        EmailSendResponseDto responseDto = emailVerificationService.sendVerificationCode(reqDto.email());
        return RsData.of("200", "인증번호가 발송되었습니다. 메일함을 확인해 주세요.", responseDto);
    }

    /**
     * 이메일 인증번호 검증 API
     * - 일치 시 인증 완료 플래그 저장(TTL 10분), 이 시간 내에 회원가입 필요
     */
    @PostMapping("/email/verify")
    public RsData<Void> verifyEmailCode(@Valid @RequestBody EmailVerifyReqDto reqDto) {
        emailVerificationService.verifyCode(reqDto.email(), reqDto.code());
        return RsData.of("200", "이메일 인증이 완료되었습니다.", null);
    }
}
