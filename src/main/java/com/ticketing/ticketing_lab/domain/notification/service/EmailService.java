package com.ticketing.ticketing_lab.domain.notification.service;

public interface EmailService {

    /**
     * 예매 완료 안내 이메일 발송
     *
     * @param toEmail 수신자 이메일 주소
     * @param ticketTitle 예매한 티켓 명칭
     */
    void sendOrderConfirmationEmail(String toEmail, String ticketTitle);
}
