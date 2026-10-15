package com.ticketing.ticketing_lab.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationCodeGeneratorTest {

    private final VerificationCodeGenerator generator = new VerificationCodeGenerator();

    @Test
    @DisplayName("항상 앞자리 0을 포함한 6자리 숫자 문자열을 생성한다")
    void generateSixDigits() {
        for (int i = 0; i < 10_000; i++) {
            assertThat(generator.generate()).matches("^\\d{6}$");
        }
    }

    @Test
    @DisplayName("연속 생성 시 충분히 다양한 값이 생성된다 (고정값/편향 방지)")
    void generateRandomly() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            codes.add(generator.generate());
        }
        // 1,000,000 경우의 수에서 1,000번 생성 시 중복은 극히 드묾
        assertThat(codes.size()).isGreaterThan(950);
    }
}
