package com.ticketing.ticketing_lab.domain.user;

import com.ticketing.ticketing_lab.domain.user.repository.UserRepository;
import com.ticketing.ticketing_lab.domain.user.v1.dto.UserResponseDto;
import com.ticketing.ticketing_lab.domain.user.v1.dto.UserSignUpReqDto;
import com.ticketing.ticketing_lab.domain.user.v1.service.EmailVerificationService;
import com.ticketing.ticketing_lab.domain.user.v1.service.UserService;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import com.ticketing.ticketing_lab.global.util.VerificationCodeGenerator;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 실제 Redis를 사용한 이메일 인증 TTL 통합 테스트
 * - 5분을 기다릴 수 없으므로 인증번호 TTL을 2초로 줄여 만료 동작을 검증
 * - SMTP 발송(JavaMailSender)과 난수 생성기는 Mock으로 대체
 */
@SpringBootTest(properties = {
        "email.verification.code-ttl-seconds=2",
        "email.verification.resend-cooldown-seconds=1"
})
class EmailVerificationIntegrationTest {

    private static final String EMAIL = "email-verify-it@test.com";
    private static final String CODE = "135790";
    private static final String CODE_KEY = "email_code:" + EMAIL;
    private static final String VERIFIED_KEY = "email_verified:" + EMAIL;
    private static final String ATTEMPTS_KEY = "email_code_attempts:" + EMAIL;
    private static final String COOLDOWN_KEY = "email_send_cooldown:" + EMAIL;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private VerificationCodeGenerator verificationCodeGenerator;

    @BeforeEach
    void setUp() {
        cleanUp();
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
        given(verificationCodeGenerator.generate()).willReturn(CODE);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        stringRedisTemplate.delete(List.of(CODE_KEY, VERIFIED_KEY, ATTEMPTS_KEY, COOLDOWN_KEY));
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("TTL 이내에 올바른 인증번호를 입력하면 인증에 성공하고, 인증 완료 플래그가 TTL과 함께 저장된다")
    void verifyWithinTtl_success() {
        // when: 발송
        emailVerificationService.sendVerificationCode(EMAIL);

        // then: 인증번호가 TTL과 함께 저장됨
        assertThat(stringRedisTemplate.opsForValue().get(CODE_KEY)).isEqualTo(CODE);
        Long codeTtl = stringRedisTemplate.getExpire(CODE_KEY, TimeUnit.SECONDS);
        assertThat(codeTtl).isBetween(1L, 2L);
        verify(mailSender, org.mockito.Mockito.timeout(3000)).send(any(MimeMessage.class));

        // when: 검증
        emailVerificationService.verifyCode(EMAIL, CODE);

        // then: 인증번호는 1회성으로 소모, 인증 완료 플래그는 TTL(600초)과 함께 저장
        assertThat(stringRedisTemplate.hasKey(CODE_KEY)).isFalse();
        assertThat(emailVerificationService.isVerified(EMAIL)).isTrue();
        Long verifiedTtl = stringRedisTemplate.getExpire(VERIFIED_KEY, TimeUnit.SECONDS);
        assertThat(verifiedTtl).isBetween(590L, 600L);
    }

    @Test
    @DisplayName("Redis TTL이 만료되어 인증번호 키가 사라지면 인증에 실패한다")
    void verifyAfterTtlExpired_fail() throws InterruptedException {
        // given
        emailVerificationService.sendVerificationCode(EMAIL);
        assertThat(stringRedisTemplate.hasKey(CODE_KEY)).isTrue();

        // when: TTL(2초) 경과 대기
        Thread.sleep(2_500);

        // then: Redis가 키를 자동 삭제하여 인증 실패
        assertThat(stringRedisTemplate.hasKey(CODE_KEY)).isFalse();
        assertThatThrownBy(() -> emailVerificationService.verifyCode(EMAIL, CODE))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_CODE_EXPIRED);
        assertThat(emailVerificationService.isVerified(EMAIL)).isFalse();
    }

    @Test
    @DisplayName("미인증 이메일은 회원가입이 거부되고, 인증 완료 후에는 가입 성공 및 인증 플래그가 소모된다")
    void signUpRequiresEmailVerification() {
        UserSignUpReqDto reqDto = new UserSignUpReqDto(EMAIL, "password123!", "LOCAL");

        // 1. 미인증 상태 가입 시도 → 거부
        assertThatThrownBy(() -> userService.signUp(reqDto))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_NOT_VERIFIED);
        assertThat(userRepository.existsByEmail(EMAIL)).isFalse();

        // 2. 발송 → 검증 → 가입 성공
        emailVerificationService.sendVerificationCode(EMAIL);
        emailVerificationService.verifyCode(EMAIL, CODE);
        UserResponseDto response = userService.signUp(reqDto);

        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(userRepository.existsByEmail(EMAIL)).isTrue();

        // 3. 인증 완료 플래그는 가입과 함께 소모되어 재사용 불가
        assertThat(emailVerificationService.isVerified(EMAIL)).isFalse();
    }
}
