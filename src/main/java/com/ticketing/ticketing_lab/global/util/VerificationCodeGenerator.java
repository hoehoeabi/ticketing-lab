package com.ticketing.ticketing_lab.global.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 6자리 숫자 인증번호 생성기
 * - java.util.Random은 시드 예측이 가능하므로, 암호학적으로 안전한 SecureRandom 사용
 * - 빈(Bean)으로 등록하여 테스트에서 고정된 인증번호로 대체(Mock) 가능
 */
@Component
public class VerificationCodeGenerator {

    private static final int CODE_LENGTH = 6;
    private static final int UPPER_BOUND = 1_000_000; // 000000 ~ 999999

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 000000 ~ 999999 범위의 6자리 숫자 문자열 생성 (앞자리 0 패딩 유지)
     */
    public String generate() {
        return String.format("%0" + CODE_LENGTH + "d", secureRandom.nextInt(UPPER_BOUND));
    }
}
