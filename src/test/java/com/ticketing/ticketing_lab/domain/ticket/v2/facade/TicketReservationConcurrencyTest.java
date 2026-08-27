package com.ticketing.ticketing_lab.domain.ticket.v2.facade;

import com.ticketing.ticketing_lab.domain.order.repository.TicketOrderRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TicketReservationConcurrencyTest {

    @Autowired
    private RedissonLockTicketFacade redissonLockTicketFacade;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketOrderRepository ticketOrderRepository;

    private Ticket savedTicket;
    private final List<User> users = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 1. 티켓 1개 생성 (재고 100개)
        Ticket ticket = Ticket.builder()
                .title("아이유 콘서트 선착순 예매")
                .totalQuantity(100)
                .remainingQuantity(100)
                .openAt(LocalDateTime.now())
                .build();
        savedTicket = ticketRepository.save(ticket);

        // 2. 유저 100명 생성
        for (int i = 0; i < 100; i++) {
            User user = User.builder()
                    .email("user" + i + "@test.com")
                    .password("password")
                    .provider("LOCAL")
                    .role(Role.ROLE_USER)
                    .build();
            users.add(user);
        }
        userRepository.saveAll(users);
    }

    @AfterEach
    void tearDown() {
        // 테스트 간 데이터 오염 방지
        ticketOrderRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("100명의 유저가 동시에 예매를 요청하면 재고가 정확히 0이 되어야 한다 (Redisson Lock)")
    void concurrentReservationTest() throws InterruptedException {
        // given
        int threadCount = 100;
        // 32개의 스레드가 동시에 작업을 처리하도록 스레드 풀 생성 (동시성 극대화)
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        // 모든 스레드의 작업이 끝날 때까지 메인 스레드를 대기시키기 위한 장치
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            User currentUser = users.get(i);
            executorService.submit(() -> {
                try {
                    redissonLockTicketFacade.reserveTicket(currentUser.getId(), savedTicket.getId());
                } catch (Exception e) {
                    System.out.println("예외 발생: " + e.getMessage());
                } finally {
                    latch.countDown(); // 작업 완료 시 카운트 감소
                }
            });
        }

        latch.await(); // 100개의 요청이 모두 끝날 때까지 대기

        // then
        Ticket findTicket = ticketRepository.findById(savedTicket.getId()).orElseThrow();
        long orderCount = ticketOrderRepository.count();

        System.out.println("남은 티켓 수량: " + findTicket.getRemainingQuantity());
        System.out.println("생성된 주문 수: " + orderCount);

        // 잔여 수량이 0인지 검증 (Race Condition 발생 시 0보다 큰 값이 남음)
        assertThat(findTicket.getRemainingQuantity()).isEqualTo(0);

        // 실제 생성된 주문(Order) 데이터가 100개인지 검증
        assertThat(orderCount).isEqualTo(100);
    }
}