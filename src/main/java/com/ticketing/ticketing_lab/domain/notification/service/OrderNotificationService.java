package com.ticketing.ticketing_lab.domain.notification.service;

import com.ticketing.ticketing_lab.domain.notification.entity.OrderNotification;
import com.ticketing.ticketing_lab.domain.notification.enums.NotificationStatus;
import com.ticketing.ticketing_lab.domain.notification.repository.OrderNotificationRepository;
import com.ticketing.ticketing_lab.domain.order.entity.TicketOrder;
import com.ticketing.ticketing_lab.domain.order.repository.TicketOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private final OrderNotificationRepository orderNotificationRepository;
    private final TicketOrderRepository ticketOrderRepository;

    /**
     * 알림 발송 전 PENDING 상태의 알림 이력 생성 (별도 독립 트랜잭션으로 커밋하여 이력 보존)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createPendingNotification(Long orderId, String receiverEmail) {
        TicketOrder order = ticketOrderRepository.getReferenceById(orderId);

        OrderNotification notification = OrderNotification.builder()
                .order(order)
                .receiverEmail(receiverEmail)
                .status(NotificationStatus.PENDING)
                .build();

        OrderNotification saved = orderNotificationRepository.save(notification);
        log.info("[OrderNotificationService] 알림 발송 대기(PENDING) 기록 완료 - notificationId: {}, orderId: {}",
                saved.getId(), orderId);
        return saved.getId();
    }

    /**
     * 알림 발송 성공 시 상태를 SENT로 변경
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsSent(Long notificationId) {
        orderNotificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.markAsSent();
            log.info("[OrderNotificationService] 알림 발송 성공(SENT) 업데이트 완료 - notificationId: {}", notificationId);
        });
    }

    /**
     * 알림 발송 실패 시 상태를 FAILED로 변경
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long notificationId) {
        orderNotificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.markAsFailed();
            log.warn("[OrderNotificationService] 알림 발송 실패(FAILED) 업데이트 완료 - notificationId: {}", notificationId);
        });
    }
}
