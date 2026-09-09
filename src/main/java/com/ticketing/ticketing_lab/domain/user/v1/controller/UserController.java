package com.ticketing.ticketing_lab.domain.user.v1.controller;

import com.ticketing.ticketing_lab.domain.user.v1.dto.AccessTokenResponseDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.TokenResponseDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.UserLoginReqDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.UserResponseDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.UserSignUpReqDto;
import com.ticketing.ticketing_lab.domain.user.v1.service.UserService;
import com.ticketing.ticketing_lab.global.common.RsData;
import com.ticketing.ticketing_lab.global.security.user.CustomUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 회원가입 API
     */
    @PostMapping("/signup")
    public ResponseEntity<RsData<UserResponseDto>> signUp(@Valid @RequestBody UserSignUpReqDto reqDto) {
        UserResponseDto responseDto = userService.signUp(reqDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RsData.of("201", "회원가입이 성공적으로 완료되었습니다.", responseDto));
    }

    /**
     * 로그인 API
     * - Access Token은 응답 바디로 반환
     * - Refresh Token은 HttpOnly/Secure 쿠키로 전달 (RTR 지원)
     */
    @PostMapping("/login")
    public RsData<AccessTokenResponseDto> login(
            @Valid @RequestBody UserLoginReqDto reqDto,
            HttpServletResponse response) {

        TokenResponseDto tokenResponse = userService.login(reqDto);

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
                "로그인에 성공하였습니다.",
                new AccessTokenResponseDto(tokenResponse.accessToken())
        );
    }

    /**
     * 내 프로필 조회 API (인증 필요)
     */
    @GetMapping("/me")
    public RsData<UserResponseDto> getMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponseDto responseDto = userService.getUserProfile(userDetails.getUserId());
        return RsData.of("200", "내 프로필 조회가 완료되었습니다.", responseDto);
    }
}
