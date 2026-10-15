package com.ticketing.ticketing_lab.domain.user.v1.service;

import com.ticketing.ticketing_lab.domain.user.repository.UserRepository;
import com.ticketing.ticketing_lab.domain.user.v1.dto.EmailSendResponseDto;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import com.ticketing.ticketing_lab.global.util.VerificationCodeGenerator;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "test@test.com";
    private static final String CODE = "123456";
    private static final String CODE_KEY = "email_code:" + EMAIL;
    private static final String VERIFIED_KEY = "email_verified:" + EMAIL;
    private static final String ATTEMPTS_KEY = "email_code_attempts:" + EMAIL;
    private static final String COOLDOWN_KEY = "email_send_cooldown:" + EMAIL;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationCodeGenerator verificationCodeGenerator;

    @Nested
    @DisplayName("인증번호 발송")
    class SendVerificationCode {

        @Test
        @DisplayName("성공 - 인증번호가 TTL 300초로 Redis에 저장되고 메일이 발송된다")
        void sendSuccess() throws Exception {
            // given
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), anyString(), any(Duration.class))).willReturn(true);
            given(verificationCodeGenerator.generate()).willReturn(CODE);
            given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));

            // when
            EmailSendResponseDto response = emailVerificationService.sendVerificationCode(EMAIL);

            // then
            assertThat(response.expiresInSeconds()).isEqualTo(300L);
            verify(valueOperations).set(CODE_KEY, CODE, Duration.ofSeconds(300));
            verify(stringRedisTemplate).delete(ATTEMPTS_KEY);

            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());
            MimeMessage sent = captor.getValue();
            assertThat(sent.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo(EMAIL);
            assertThat(sent.getContent().toString()).contains(CODE);
        }

        @Test
        @DisplayName("성공 - 대소문자/공백이 섞인 이메일은 정규화된 키로 저장된다")
        void sendNormalizesEmail() {
            // given
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), anyString(), any(Duration.class))).willReturn(true);
            given(verificationCodeGenerator.generate()).willReturn(CODE);
            given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));

            // when
            emailVerificationService.sendVerificationCode("  Test@TEST.com ");

            // then
            verify(valueOperations).set(CODE_KEY, CODE, Duration.ofSeconds(300));
        }

        @Test
        @DisplayName("실패 - 이미 가입된 이메일이면 메일을 보내지 않고 DUPLICATE_EMAIL 예외가 발생한다")
        void sendFailDuplicateEmail() {
            // given
            given(userRepository.existsByEmail(EMAIL)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> emailVerificationService.sendVerificationCode(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_EMAIL);
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("실패 - 재발송 쿨다운 중이면 남은 시간과 함께 EMAIL_SEND_TOO_FREQUENT 예외가 발생한다")
        void sendFailCooldown() {
            // given
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), anyString(), any(Duration.class))).willReturn(false);
            given(stringRedisTemplate.getExpire(COOLDOWN_KEY, TimeUnit.SECONDS)).willReturn(42L);

            // when & then
            assertThatThrownBy(() -> emailVerificationService.sendVerificationCode(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_SEND_TOO_FREQUENT)
                    .hasMessageContaining("42초");
            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("실패 - SMTP 발송 실패 시 저장된 인증번호/쿨다운 키를 롤백하고 EMAIL_SEND_FAILED 예외가 발생한다")
        void sendFailSmtpError() {
            // given
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), anyString(), any(Duration.class))).willReturn(true);
            given(verificationCodeGenerator.generate()).willReturn(CODE);
            given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
            willThrow(new MailSendException("SMTP 연결 실패")).given(mailSender).send(any(MimeMessage.class));

            // when & then
            assertThatThrownBy(() -> emailVerificationService.sendVerificationCode(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_SEND_FAILED);
            verify(stringRedisTemplate).delete(List.of(CODE_KEY, COOLDOWN_KEY));
        }
    }

    @Nested
    @DisplayName("인증번호 검증")
    class VerifyCode {

        @Test
        @DisplayName("성공 - 인증번호가 일치하면 인증번호는 소모되고 인증 완료 플래그가 TTL 600초로 저장된다")
        void verifySuccess() {
            // given
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(CODE_KEY)).willReturn(CODE);
            given(valueOperations.increment(ATTEMPTS_KEY)).willReturn(1L);

            // when
            emailVerificationService.verifyCode(EMAIL, CODE);

            // then
            verify(stringRedisTemplate).expire(ATTEMPTS_KEY, Duration.ofSeconds(300));
            verify(stringRedisTemplate).delete(List.of(CODE_KEY, ATTEMPTS_KEY));
            verify(valueOperations).set(VERIFIED_KEY, "VERIFIED", Duration.ofSeconds(600));
        }

        @Test
        @DisplayName("실패 - Redis에 인증번호가 없으면(TTL 만료) EMAIL_CODE_EXPIRED 예외가 발생한다")
        void verifyFailExpired() {
            // given
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(CODE_KEY)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> emailVerificationService.verifyCode(EMAIL, CODE))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_CODE_EXPIRED);
            verify(valueOperations, never()).set(eq(VERIFIED_KEY), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("실패 - 인증번호가 다르면 남은 시도 횟수와 함께 EMAIL_CODE_MISMATCH 예외가 발생한다")
        void verifyFailMismatch() {
            // given
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(CODE_KEY)).willReturn(CODE);
            given(valueOperations.increment(ATTEMPTS_KEY)).willReturn(1L);

            // when & then
            assertThatThrownBy(() -> emailVerificationService.verifyCode(EMAIL, "654321"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_CODE_MISMATCH)
                    .hasMessageContaining("남은 시도 횟수: 4회");
            verify(valueOperations, never()).set(eq(VERIFIED_KEY), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("실패 - 마지막(5회차) 시도까지 틀리면 인증번호가 폐기되고 EMAIL_CODE_ATTEMPTS_EXCEEDED 예외가 발생한다")
        void verifyFailLastAttempt() {
            // given
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(CODE_KEY)).willReturn(CODE);
            given(valueOperations.increment(ATTEMPTS_KEY)).willReturn(5L);

            // when & then
            assertThatThrownBy(() -> emailVerificationService.verifyCode(EMAIL, "654321"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
            verify(stringRedisTemplate).delete(List.of(CODE_KEY, ATTEMPTS_KEY));
        }

        @Test
        @DisplayName("실패 - 시도 횟수를 초과하면 올바른 인증번호여도 EMAIL_CODE_ATTEMPTS_EXCEEDED 예외가 발생한다")
        void verifyFailAttemptsExceeded() {
            // given
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(CODE_KEY)).willReturn(CODE);
            given(valueOperations.increment(ATTEMPTS_KEY)).willReturn(6L);

            // when & then
            assertThatThrownBy(() -> emailVerificationService.verifyCode(EMAIL, CODE))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
            verify(stringRedisTemplate).delete(List.of(CODE_KEY, ATTEMPTS_KEY));
            verify(valueOperations, never()).set(eq(VERIFIED_KEY), anyString(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("인증 완료 여부 / 소모")
    class VerifiedFlag {

        @Test
        @DisplayName("인증 완료 플래그가 존재하면 true를 반환한다")
        void isVerifiedTrue() {
            given(stringRedisTemplate.hasKey(VERIFIED_KEY)).willReturn(true);

            assertThat(emailVerificationService.isVerified("Test@test.com")).isTrue();
        }

        @Test
        @DisplayName("인증 완료 플래그가 없으면 false를 반환한다")
        void isVerifiedFalse() {
            given(stringRedisTemplate.hasKey(VERIFIED_KEY)).willReturn(false);

            assertThat(emailVerificationService.isVerified(EMAIL)).isFalse();
        }

        @Test
        @DisplayName("회원가입 완료 후 인증 완료 플래그를 삭제한다")
        void consumeVerification() {
            emailVerificationService.consumeVerification(EMAIL);

            verify(stringRedisTemplate).delete(VERIFIED_KEY);
        }
    }
}
