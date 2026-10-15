package com.ticketing.ticketing_lab.domain.user.v1.dto;

/**
 * 인증번호 발송 응답
 *
 * @param expiresInSeconds 인증번호 유효 시간(초) - 프론트엔드 카운트다운 타이머 기준값
 */
public record EmailSendResponseDto(
        long expiresInSeconds
) {
}
