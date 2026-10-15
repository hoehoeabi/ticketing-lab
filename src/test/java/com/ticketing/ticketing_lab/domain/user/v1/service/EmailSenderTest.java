package com.ticketing.ticketing_lab.domain.user.v1.service;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSenderTest {

    @InjectMocks
    private EmailSender emailSender;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("비동기 메일 발송 성공 - JavaMailSender를 통해 메시지가 전송된다")
    void sendVerificationEmailAsync_success() throws Exception {
        // given
        String toEmail = "test@test.com";
        String code = "123456";
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));

        // when
        emailSender.sendVerificationEmailAsync(toEmail, code, 300L, "codeKey", "cooldownKey");

        // then
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo(toEmail);
        assertThat(sent.getContent().toString()).contains(code);
    }

    @Test
    @DisplayName("비동기 메일 발송 실패 - SMTP 전송 실패 시 세팅된 Redis 키를 롤백(삭제)한다")
    void sendVerificationEmailAsync_failureRollsBackRedis() {
        // given
        String toEmail = "test@test.com";
        String code = "123456";
        String codeKey = "email_code:test@test.com";
        String cooldownKey = "email_send_cooldown:test@test.com";

        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
        willThrow(new MailSendException("SMTP 연결 에러")).given(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

        // when
        emailSender.sendVerificationEmailAsync(toEmail, code, 300L, codeKey, cooldownKey);

        // then
        verify(stringRedisTemplate).delete(List.of(codeKey, cooldownKey));
    }
}
