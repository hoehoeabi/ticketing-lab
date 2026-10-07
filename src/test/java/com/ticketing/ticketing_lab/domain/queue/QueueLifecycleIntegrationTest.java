package com.ticketing.ticketing_lab.domain.queue;

import com.ticketing.ticketing_lab.domain.order.repository.TicketOrderRepository;
import com.ticketing.ticketing_lab.domain.order.v1.controller.TicketOrderController;
import com.ticketing.ticketing_lab.domain.queue.enums.QueueStatus;
import com.ticketing.ticketing_lab.domain.queue.interceptor.QueueActiveCheckInterceptor;
import com.ticketing.ticketing_lab.domain.queue.scheduler.QueuePromotionScheduler;
import com.ticketing.ticketing_lab.domain.queue.v1.dto.QueueResponseDto;
import com.ticketing.ticketing_lab.domain.queue.v1.service.QueueService;
import com.ticketing.ticketing_lab.domain.ticket.entity.Ticket;
import com.ticketing.ticketing_lab.domain.ticket.repository.TicketRepository;
import com.ticketing.ticketing_lab.domain.user.entity.User;
import com.ticketing.ticketing_lab.domain.user.enums.Role;
import com.ticketing.ticketing_lab.domain.user.repository.UserRepository;
import com.ticketing.ticketing_lab.global.common.RsData;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import com.ticketing.ticketing_lab.global.security.user.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class QueueLifecycleIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueuePromotionScheduler queuePromotionScheduler;

    @Autowired
    private QueueActiveCheckInterceptor queueActiveCheckInterceptor;

    @Autowired
    private TicketOrderController ticketOrderController;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketOrderRepository ticketOrderRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Ticket ticket;
    private User user1;
    private User user2;
    private User user3;

    @BeforeEach
    void setUp() {
        cleanUpData();

        ticket = ticketRepository.save(Ticket.builder()
                .title("2026 리센느 콘서트")
                .totalQuantity(5)
                .remainingQuantity(5)
                .openAt(LocalDateTime.now())
                .build());

        user1 = userRepository.save(User.builder().email("user1@test.com").password("pwd").role(Role.ROLE_USER).build());
        user2 = userRepository.save(User.builder().email("user2@test.com").password("pwd").role(Role.ROLE_USER).build());
        user3 = userRepository.save(User.builder().email("user3@test.com").password("pwd").role(Role.ROLE_USER).build());
    }

    @AfterEach
    void tearDown() {
        cleanUpData();
        SecurityContextHolder.clearContext();
    }

    private void cleanUpData() {
        ticketOrderRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        if (ticket != null) {
            stringRedisTemplate.delete("queue:ticket:" + ticket.getId() + ":waiting");
            stringRedisTemplate.delete("queue:ticket:" + ticket.getId() + ":active:" + (user1 != null ? user1.getId() : 1L));
            stringRedisTemplate.delete("queue:ticket:" + ticket.getId() + ":active:" + (user2 != null ? user2.getId() : 2L));
            stringRedisTemplate.delete("queue:ticket:" + ticket.getId() + ":active:" + (user3 != null ? user3.getId() : 3L));
        }
        stringRedisTemplate.delete("queue:active-tickets");
    }

    @Test
    @DisplayName("[대기열 전체 라이프사이클 통합 검증] 진입 -> 순번 대기 -> 미승격 차단 -> 스케줄러 승격 -> 예매 성공 -> 토큰 소모")
    void fullQueueLifecycle_success() {
        Long ticketId = ticket.getId();

        // 1. 유저 1, 2, 3 대기열 순차 진입 (Enqueue)
        QueueResponseDto entry1 = queueService.enterQueue(user1.getId(), ticketId);
        QueueResponseDto entry2 = queueService.enterQueue(user2.getId(), ticketId);
        QueueResponseDto entry3 = queueService.enterQueue(user3.getId(), ticketId);

        assertThat(entry1.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(entry1.rank()).isEqualTo(0L); // 1등 (내 앞 0명)

        assertThat(entry2.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(entry2.rank()).isEqualTo(1L); // 2등 (내 앞 1명)

        assertThat(entry3.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(entry3.rank()).isEqualTo(2L); // 3등 (내 앞 2명)

        // 2. 미승격 상태(WAITING)에서 예매 API 인터셉터 진입 시도 -> 403 차단 검증
        CustomUserDetails userDetails1 = new CustomUserDetails(user1);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails1, null, userDetails1.getAuthorities())
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders/" + ticketId);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("ticketId", String.valueOf(ticketId)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> queueActiveCheckInterceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUEUE_NOT_ACTIVE);

        // 3. 스케줄러를 통한 승격 시뮬레이션 (상위 2명 승격)
        long promoted = queueService.promoteWaitingUsers(ticketId, 2L);
        assertThat(promoted).isEqualTo(2L);

        // 유저 1, 2는 ACTIVE 상태로 변경됨
        assertThat(queueService.getQueueStatus(user1.getId(), ticketId).status()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(queueService.getQueueStatus(user2.getId(), ticketId).status()).isEqualTo(QueueStatus.ACTIVE);

        // 유저 3은 여전히 WAITING, 앞 대기자가 빠져 rank가 0으로 갱신됨
        QueueResponseDto status3 = queueService.getQueueStatus(user3.getId(), ticketId);
        assertThat(status3.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(status3.rank()).isEqualTo(0L);

        // 4. 승격된 유저1 인터셉터 통과 검증
        boolean passed = queueActiveCheckInterceptor.preHandle(request, response, new Object());
        assertThat(passed).isTrue();

        // 5. 유저1 실제 예매 호출 (Redisson Facade 주문 생성 + 토큰 소모)
        RsData<Long> orderResult = ticketOrderController.reserveTicket(ticketId, userDetails1);
        assertThat(orderResult.getCode()).isEqualTo("200");
        assertThat(orderResult.getData()).isNotNull();

        // 티켓 잔여 재고 5 -> 4 차감 확인
        Ticket updatedTicket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(updatedTicket.getRemainingQuantity()).isEqualTo(4);

        // 예매 완료 후 유저1의 Active 토큰 즉시 소모(삭제) 확인
        assertThat(queueService.isActive(user1.getId(), ticketId)).isFalse();
        assertThat(queueService.getQueueStatus(user1.getId(), ticketId).status()).isEqualTo(QueueStatus.NOT_IN_QUEUE);

        // 6. 스케줄러 전체 일괄 승격 실행 (남은 유저3 승격)
        queuePromotionScheduler.schedulePromotion();
        assertThat(queueService.getQueueStatus(user3.getId(), ticketId).status()).isEqualTo(QueueStatus.ACTIVE);

        // 유저3 예매 호출
        CustomUserDetails userDetails3 = new CustomUserDetails(user3);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails3, null, userDetails3.getAuthorities())
        );
        RsData<Long> orderResult3 = ticketOrderController.reserveTicket(ticketId, userDetails3);
        assertThat(orderResult3.getCode()).isEqualTo("200");

        // 최종 재고 3개 확인
        Ticket finalTicket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(finalTicket.getRemainingQuantity()).isEqualTo(3);
    }
}
