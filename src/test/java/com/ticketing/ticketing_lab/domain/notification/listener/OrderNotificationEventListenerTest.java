package com.ticketing.ticketing_lab.domain.notification.listener;

import com.ticketing.ticketing_lab.domain.notification.entity.OrderNotification;
import com.ticketing.ticketing_lab.domain.notification.enums.NotificationStatus;
import com.ticketing.ticketing_lab.domain.notification.repository.OrderNotificationRepository;
import com.ticketing.ticketing_lab.domain.order.entity.TicketOrder;
import com.ticketing.ticketing_lab.domain.order.enums.OrderStatus;
import com.ticketing.ticketing_lab.domain.order.event.OrderCreatedEvent;
import com.ticketing.ticketing_lab.domain.order.repository.TicketOrderRepository;
import com.ticketing.ticketing_lab.domain.order.v1.service.TicketOrderService;
import com.ticketing.ticketing_lab.domain.ticket.entity.Ticket;
import com.ticketing.ticketing_lab.domain.ticket.repository.TicketRepository;
import com.ticketing.ticketing_lab.domain.user.entity.User;
import com.ticketing.ticketing_lab.domain.user.enums.Role;
import com.ticketing.ticketing_lab.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderNotificationEventListenerTest {

    @Autowired
    private TicketOrderService ticketOrderService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketOrderRepository ticketOrderRepository;

    @Autowired
    private OrderNotificationRepository orderNotificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private User savedUser;
    private Ticket savedTicket;

    @BeforeEach
    void setUp() {
        orderNotificationRepository.deleteAllInBatch();
        ticketOrderRepository.deleteAllInBatch();

        savedUser = userRepository.save(User.builder()
                .email("buyer@test.com")
                .password("encoded_pwd")
                .provider("LOCAL")
                .role(Role.ROLE_USER)
                .build());

        savedTicket = ticketRepository.save(Ticket.builder()
                .title("윤진석 2026 월드투어 콘서트")
                .totalQuantity(10)
                .remainingQuantity(10)
                .openAt(LocalDateTime.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        orderNotificationRepository.deleteAllInBatch();
        ticketOrderRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("[비동기 알림 이벤트 테스트] 주문 완료 시 트랜잭션 커밋 후 비동기로 알림이 발송되고 SENT 상태로 저장된다")
    void orderCreatedEvent_asyncNotification_success() throws InterruptedException {
        // when: 티켓 주문 생성 (트랜잭션 커밋 -> OrderCreatedEvent 비동기 리스너 트리거)
        Long orderId = ticketOrderService.createOrder(savedUser.getId(), savedTicket.getId());

        // then: 비동기 스레드 풀에서 이메일 발송 및 상태 업데이트가 완료될 때까지 최대 3초 대기
        OrderNotification notification = null;
        for (int i = 0; i < 30; i++) {
            Optional<OrderNotification> optional = orderNotificationRepository.findByOrderId(orderId);
            if (optional.isPresent() && optional.get().getStatus() == NotificationStatus.SENT) {
                notification = optional.get();
                break;
            }
            Thread.sleep(100);
        }

        assertThat(notification).isNotNull();
        assertThat(notification.getReceiverEmail()).isEqualTo("buyer@test.com");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("[트랜잭션 롤백 테스트] 주문 도중 예외로 트랜잭션이 롤백되면 알림 이벤트가 실행되지 않는다")
    void orderCreatedEvent_rollback_noNotification() throws InterruptedException {
        // given: 외래키 제약조건을 만족하도록 유효한 TicketOrder를 먼저 하나 저장
        TicketOrder order = ticketOrderRepository.save(TicketOrder.builder()
                .user(savedUser)
                .ticket(savedTicket)
                .status(OrderStatus.SUCCESS)
                .build());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // when: 트랜잭션 내에서 이벤트를 발행한 직후 강제로 롤백(예외 발생)
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            transactionTemplate.execute(status -> {
                eventPublisher.publishEvent(new OrderCreatedEvent(
                        order.getId(),
                        savedUser.getId(),
                        savedUser.getEmail(),
                        savedTicket.getTitle()
                ));
                throw new RuntimeException("강제 롤백 유도 예외");
            });
        });

        // then: 500ms 대기 후에도 알림 데이터가 전혀 생성되지 않음을 검증
        Thread.sleep(500);
        long notificationCount = orderNotificationRepository.count();
        assertThat(notificationCount).isEqualTo(0);
    }
}
