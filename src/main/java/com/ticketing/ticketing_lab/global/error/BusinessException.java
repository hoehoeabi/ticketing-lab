package com.ticketing.ticketing_lab.global.error;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    // ErrorCode의 기본 메시지를 사용하는 생성자
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // 상황에 따라 동적 커스텀 메시지를 넣고 싶을 때 사용하는 생성자
    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
