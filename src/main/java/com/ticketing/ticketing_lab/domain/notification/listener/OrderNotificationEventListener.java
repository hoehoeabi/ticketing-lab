package com.ticketing.ticketing_lab.domain.notification.listener;

import com.ticketing.ticketing_lab.domain.notification.service.EmailService;
import com.ticketing.ticketing_lab.domain.notification.service.OrderNotificationService;
import com.ticketing.ticketing_lab.domain.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationEventListener {

    private final OrderNotificationService orderNotificationService;
    private final EmailService emailService;

    /**
     * 주문 완료 이벤트를 비동기로 수신하여 이메일 발송 및 알림 상태 기록
     * - @Async("asyncTaskExecutor"): AsyncConfig에 설정된 별도 스레드 풀에서 비동기 실행
     * - @TransactionalEventListener(phase = AFTER_COMMIT): 원본 주문 트랜잭션이 성공적으로 커밋된 후에만 실행
     */
    @Async("asyncTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[OrderNotificationEventListener] [{}] 주문 완료 비동기 이벤트 수신 - orderId: {}, email: {}",
                Thread.currentThread().getName(), event.orderId(), event.receiverEmail());

        Long notificationId = null;
        try {
            // 1. PENDING 상태로 알림 이력 저장
            notificationId = orderNotificationService.createPendingNotification(event.orderId(), event.receiverEmail());

            // 2. 외부 이메일 발송 (네트워크 I/O 작업 중 DB 커넥션을 점유하지 않음)
            emailService.sendOrderConfirmationEmail(event.receiverEmail(), event.ticketTitle());

            // 3. 발송 완료 상태(SENT)로 갱신
            orderNotificationService.markAsSent(notificationId);

        } catch (Exception e) {
            log.error("[OrderNotificationEventListener] 알림 처리 중 예외 발생 - orderId: {}, notificationId: {}",
                    event.orderId(), notificationId, e);
            if (notificationId != null) {
                orderNotificationService.markAsFailed(notificationId);
            }
        }
    }
}
