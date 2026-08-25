package com.ticketing.ticketing_lab.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request (잘못된 요청 / 유효성 검증 실패)
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "400", "입력값이 올바르지 않습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "400", "입력 타입이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "405", "지원하지 않는 HTTP 메서드입니다."),
    TICKET_SOLD_OUT(HttpStatus.BAD_REQUEST, "400", "티켓이 모두 매진되었습니다."),

    // 401 Unauthorized (인증/토큰 관련)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "401", "인증 정보가 유효하지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "401", "유효하지 않거나 만료된 Refresh Token입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "401", "이미 로그아웃되었거나 만료된 토큰입니다."),
    TOKEN_THEFT_DETECTED(HttpStatus.UNAUTHORIZED, "401", "보안 위협이 감지되어 토큰이 무효화되었습니다. 다시 로그인해 주세요."),

    // 403 Forbidden (권한 없음)
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "403", "해당 리소스에 접근할 권한이 없습니다."),
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "404", "해당 티켓을 찾을 수 없습니다."),

    // 404 Not Found (리소스 없음)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "404", "해당 사용자를 찾을 수 없습니다."),

    // 409 Conflict (중복 / 충돌)
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "409", "이미 존재하는 이메일입니다."),
    LOCK_ACQUISITION_FAILED(HttpStatus.CONFLICT, "409", "예매 요청이 많아 지연되고 있습니다. 다시 시도해 주세요."),
    
    // 500 Internal Server Error (서버 내부 에러)
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
