package com.ticketing.ticketing_lab.domain.queue;

import com.ticketing.ticketing_lab.domain.order.v1.controller.TicketOrderController;
import com.ticketing.ticketing_lab.domain.queue.enums.QueueStatus;
import com.ticketing.ticketing_lab.domain.queue.v1.dto.QueueResponseDto;
import com.ticketing.ticketing_lab.domain.queue.v1.service.QueueService;
import com.ticketing.ticketing_lab.domain.ticket.entity.Ticket;
import com.ticketing.ticketing_lab.domain.ticket.repository.TicketRepository;
import com.ticketing.ticketing_lab.domain.user.entity.User;
import com.ticketing.ticketing_lab.domain.user.enums.Role;
import com.ticketing.ticketing_lab.domain.user.repository.UserRepository;
import com.ticketing.ticketing_lab.global.common.RsData;
import com.ticketing.ticketing_lab.global.security.user.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "queue.fast-path.enabled=true")
class QueueFastPathIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private TicketOrderController ticketOrderController;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.ticketing.ticketing_lab.domain.order.repository.TicketOrderRepository ticketOrderRepository;

    @Autowired
    private com.ticketing.ticketing_lab.domain.notification.repository.OrderNotificationRepository orderNotificationRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Ticket ticket;
    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        cleanUp();

        ticket = ticketRepository.saveAndFlush(Ticket.builder()
                .title("아이유 2026 단독 콘서트")
                .totalQuantity(10)
                .remainingQuantity(10)
                .openAt(LocalDateTime.now())
                .build());

        user1 = userRepository.saveAndFlush(User.builder().email("fast1@test.com").password("pwd").role(Role.ROLE_USER).build());
        user2 = userRepository.saveAndFlush(User.builder().email("fast2@test.com").password("pwd").role(Role.ROLE_USER).build());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        SecurityContextHolder.clearContext();
    }

    private void cleanUp() {
        orderNotificationRepository.deleteAllInBatch();
        ticketOrderRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        if (ticket != null) {
            stringRedisTemplate.delete("queue:ticket:" + ticket.getId() + ":waiting");
            stringRedisTemplate.delete("queue:ticket:" + ticket.getId() + ":active:" + (user1 != null ? user1.getId() : 1L));
            stringRedisTemplate.delete("queue:ticket:" + ticket.getId() + ":active:" + (user2 != null ? user2.getId() : 2L));
        }
        stringRedisTemplate.delete("queue:active-tickets");
    }

    @Test
    @DisplayName("[Fast-Path] 대기열이 비어있으면 1초 대기 없이 즉시 ACTIVE 상태를 반환받고 바로 예매할 수 있다")
    void fastPath_immediateActiveAndOrder() {
        Long ticketId = ticket.getId();

        // 1. 대기자 0명 상태에서 진입 -> 스케줄러 1초 대기 없이 0초 만에 즉시 ACTIVE
        QueueResponseDto entryResponse = queueService.enterQueue(user1.getId(), ticketId);
        assertThat(entryResponse.status()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(entryResponse.rank()).isEqualTo(0L);

        // Redis에도 즉시 ACTIVE 토큰이 세팅되어 있음
        assertThat(queueService.isActive(user1.getId(), ticketId)).isTrue();

        // 2. 스케줄러 대기 없이 즉시 예매 호출 -> 성공
        CustomUserDetails userDetails = new CustomUserDetails(user1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        RsData<Long> orderResult = ticketOrderController.reserveTicket(ticketId, userDetails);
        assertThat(orderResult.getCode()).isEqualTo("200");

        // 잔여 수량 10 -> 9 차감 및 ACTIVE 토큰 소모 완료
        Ticket updatedTicket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(updatedTicket.getRemainingQuantity()).isEqualTo(9);
        assertThat(queueService.isActive(user1.getId(), ticketId)).isFalse();
    }
}
