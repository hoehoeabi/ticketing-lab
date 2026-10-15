package com.ticketing.ticketing_lab.domain.user.v1.service;

import com.ticketing.ticketing_lab.domain.user.repository.UserRepository;
import com.ticketing.ticketing_lab.domain.user.v1.dto.EmailSendResponseDto;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import com.ticketing.ticketing_lab.global.util.VerificationCodeGenerator;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 이메일 인증번호 발송 / 검증 서비스
 *
 * [Redis Key 설계]
 * - email_code:{email}          : 6자리 인증번호 (TTL 5분)  → 만료 시 자동 삭제되어 RDB에 쌓이지 않음
 * - email_verified:{email}      : 인증 완료 플래그 (TTL 10분) → 이 시간 안에 회원가입을 완료해야 함
 * - email_code_attempts:{email} : 인증번호 검증 시도 횟수 (6자리 무차별 대입 방지)
 * - email_send_cooldown:{email} : 재발송 쿨다운 (메일 폭탄 / SMTP 발송 할당량 소진 방지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final String CODE_KEY_PREFIX = "email_code:";
    static final String VERIFIED_KEY_PREFIX = "email_verified:";
    static final String ATTEMPTS_KEY_PREFIX = "email_code_attempts:";
    static final String COOLDOWN_KEY_PREFIX = "email_send_cooldown:";

    private static final String VERIFIED_VALUE = "VERIFIED";
    private static final String MAIL_SUBJECT = "[Ticketing Lab] 회원가입 이메일 인증번호 안내";

    @Value("${email.verification.code-ttl-seconds:300}")
    private long codeTtlSeconds = 300L;

    @Value("${email.verification.verified-ttl-seconds:600}")
    private long verifiedTtlSeconds = 600L;

    @Value("${email.verification.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds = 60L;

    @Value("${email.verification.max-attempts:5}")
    private long maxAttempts = 5L;

    @Value("${spring.mail.username:}")
    private String fromAddress = "";

    private final StringRedisTemplate stringRedisTemplate;
    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final VerificationCodeGenerator verificationCodeGenerator;

    /**
     * 인증번호 발송
     * 1. 이미 가입된 이메일이면 메일을 보내지 않고 즉시 거부
     * 2. 재발송 쿨다운 확인 (SET NX EX로 원자적 처리)
     * 3. 6자리 난수 생성 후 Redis 저장 (TTL 5분, 재발송 시 이전 번호는 덮어써져 무효화)
     * 4. SMTP 메일 발송 (실패 시 저장한 키를 정리하여 즉시 재시도 가능하도록 롤백)
     */
    public EmailSendResponseDto sendVerificationCode(String rawEmail) {
        String email = normalize(rawEmail);

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        String cooldownKey = COOLDOWN_KEY_PREFIX + email;
        String codeKey = CODE_KEY_PREFIX + email;

        // 재발송 쿨다운: 키가 없을 때만 세팅되므로 동시 요청이 와도 1건만 통과
        Boolean acquired = ops.setIfAbsent(cooldownKey, "1", Duration.ofSeconds(resendCooldownSeconds));
        if (!Boolean.TRUE.equals(acquired)) {
            Long remainSec = stringRedisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            long waitSec = (remainSec != null && remainSec > 0) ? remainSec : resendCooldownSeconds;
            throw new BusinessException(ErrorCode.EMAIL_SEND_TOO_FREQUENT,
                    String.format("인증번호는 %d초 후에 다시 요청할 수 있습니다.", waitSec));
        }

        String code = verificationCodeGenerator.generate();
        ops.set(codeKey, code, Duration.ofSeconds(codeTtlSeconds));
        // 새 인증번호가 발급되었으므로 이전 번호에 대한 시도 횟수는 초기화
        stringRedisTemplate.delete(ATTEMPTS_KEY_PREFIX + email);

        try {
            sendMail(email, code);
        } catch (MailException | MessagingException e) {
            stringRedisTemplate.delete(List.of(codeKey, cooldownKey));
            log.error("[이메일 인증] 인증 메일 발송 실패 - email: {}", email, e);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }

        log.info("[이메일 인증] 인증번호 발송 완료 - email: {}, ttl: {}s", email, codeTtlSeconds);
        return new EmailSendResponseDto(codeTtlSeconds);
    }

    /**
     * 인증번호 검증
     * - Redis에 키가 없으면(TTL 만료 / 미발송) 실패
     * - 시도 횟수 초과 시 인증번호 자체를 폐기하여 재발송을 강제
     * - 성공 시 인증번호는 1회성으로 소모하고, 인증 완료 플래그를 저장 (TTL 10분)
     */
    public void verifyCode(String rawEmail, String inputCode) {
        String email = normalize(rawEmail);
        String codeKey = CODE_KEY_PREFIX + email;
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;

        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        String savedCode = ops.get(codeKey);
        if (savedCode == null) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED);
        }

        // 시도 횟수 증가 (첫 시도 시 인증번호와 동일한 수명을 부여하여 고아 키 방지)
        Long attempts = ops.increment(attemptsKey);
        long currentAttempts = attempts != null ? attempts : 1L;
        if (currentAttempts == 1L) {
            stringRedisTemplate.expire(attemptsKey, Duration.ofSeconds(codeTtlSeconds));
        }
        if (currentAttempts > maxAttempts) {
            stringRedisTemplate.delete(List.of(codeKey, attemptsKey));
            throw new BusinessException(ErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
        }

        // 타이밍 공격 방지를 위한 상수 시간 비교
        boolean matched = MessageDigest.isEqual(
                savedCode.getBytes(StandardCharsets.UTF_8),
                inputCode.getBytes(StandardCharsets.UTF_8));

        if (!matched) {
            long remaining = maxAttempts - currentAttempts;
            if (remaining <= 0) {
                stringRedisTemplate.delete(List.of(codeKey, attemptsKey));
                throw new BusinessException(ErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
            }
            throw new BusinessException(ErrorCode.EMAIL_CODE_MISMATCH,
                    String.format("인증번호가 일치하지 않습니다. (남은 시도 횟수: %d회)", remaining));
        }

        stringRedisTemplate.delete(List.of(codeKey, attemptsKey));
        ops.set(VERIFIED_KEY_PREFIX + email, VERIFIED_VALUE, Duration.ofSeconds(verifiedTtlSeconds));

        log.info("[이메일 인증] 인증 성공 - email: {}, 가입 가능 시간: {}s", email, verifiedTtlSeconds);
    }

    /**
     * 회원가입 가능 여부 (인증 완료 플래그 존재 여부) 확인
     */
    public boolean isVerified(String rawEmail) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(VERIFIED_KEY_PREFIX + normalize(rawEmail)));
    }

    /**
     * 회원가입 완료 후 인증 완료 플래그 소모 (재사용 방지)
     */
    public void consumeVerification(String rawEmail) {
        stringRedisTemplate.delete(VERIFIED_KEY_PREFIX + normalize(rawEmail));
    }

    /**
     * 대소문자/공백 차이로 동일 이메일이 다른 키로 저장되는 것을 방지
     */
    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void sendMail(String toEmail, String code) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());

        if (StringUtils.hasText(fromAddress)) {
            helper.setFrom(fromAddress);
        }
        helper.setTo(toEmail);
        helper.setSubject(MAIL_SUBJECT);
        helper.setText(buildMailBody(code), true);

        mailSender.send(message);
    }

    private String buildMailBody(String code) {
        long minutes = Math.max(1, codeTtlSeconds / 60);
        return """
                <div style="font-family: 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif; max-width: 480px; margin: 0 auto; padding: 32px; border: 1px solid #e2e8f0; border-radius: 16px;">
                  <h2 style="color: #0f172a; margin: 0 0 8px;">Ticketing Lab 이메일 인증</h2>
                  <p style="color: #475569; font-size: 14px; margin: 0 0 24px;">아래 인증번호를 회원가입 화면에 입력해 주세요.</p>
                  <div style="background: #eef2ff; color: #4338ca; font-size: 32px; font-weight: 700; letter-spacing: 8px; text-align: center; padding: 20px; border-radius: 12px;">%s</div>
                  <p style="color: #64748b; font-size: 12px; margin: 24px 0 0;">이 인증번호는 <b>%d분</b> 후 만료됩니다. 본인이 요청하지 않았다면 이 메일을 무시해 주세요.</p>
                </div>
                """.formatted(code, minutes);
    }
}
