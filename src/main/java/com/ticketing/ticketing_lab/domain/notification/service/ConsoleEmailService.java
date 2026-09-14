package com.ticketing.ticketing_lab.domain.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConsoleEmailService implements EmailService {

    @Override
    public void sendOrderConfirmationEmail(String toEmail, String ticketTitle) {
        log.info("[EmailService] [{}] 메일 발송 시작 - 수신자: {}, 티켓명: '{}'",
                Thread.currentThread().getName(), toEmail, ticketTitle);

        try {
            // 외부 SMTP / 메일 발송 네트워크 I/O 지연 시뮬레이션 (200ms)
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[EmailService] 메일 발송 중 인터럽트 발생", e);
            throw new RuntimeException("이메일 발송 실패", e);
        }

        log.info("[EmailService] [{}] 메일 발송 완료 - 수신자: {}, 티켓명: '{}'",
                Thread.currentThread().getName(), toEmail, ticketTitle);
    }
}
