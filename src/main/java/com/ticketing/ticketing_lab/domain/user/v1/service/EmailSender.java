package com.ticketing.ticketing_lab.domain.user.v1.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 이메일 비동기 전송 컴포넌트
 * - 별도의 스레드 풀(asyncTaskExecutor)에서 SMTP I/O를 비동기로 실행하여 톰캣 스레드 블로킹 방지
 * - 발송 실패 시 Redis에 세팅된 인증번호 및 쿨다운 키를 롤백하여 재발송 가능하도록 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender {

    private static final String MAIL_SUBJECT = "[Ticketing Lab] 회원가입 이메일 인증번호 안내";

    private final JavaMailSender mailSender;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${spring.mail.username:}")
    private String fromAddress = "";

    @Async("asyncTaskExecutor")
    public void sendVerificationEmailAsync(String toEmail, String code, long codeTtlSeconds, String codeKey, String cooldownKey) {
        log.info("[비동기 메일 발송 시작] thread: {}, to: {}", Thread.currentThread().getName(), toEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());

            if (StringUtils.hasText(fromAddress)) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(toEmail);
            helper.setSubject(MAIL_SUBJECT);
            helper.setText(buildMailBody(code, codeTtlSeconds), true);

            mailSender.send(message);
            log.info("[비동기 메일 발송 완료] to: {}", toEmail);
        } catch (MailException | MessagingException e) {
            log.error("[비동기 메일 발송 실패] to: {} - Redis 인증 정보 롤백", toEmail, e);
            stringRedisTemplate.delete(List.of(codeKey, cooldownKey));
        }
    }

    private String buildMailBody(String code, long codeTtlSeconds) {
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
