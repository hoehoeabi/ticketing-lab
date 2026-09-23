package com.ticketing.ticketing_lab.domain.ticket.v2.facade;

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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.ticketing.ticketing_lab.domain.notification.listener.OrderNotificationEventListener orderNotificationEventListener;

    @Autowired
    private TicketOrderService ticketOrderService;

    private Ticket savedTicket;
    private final List<User> users = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 1. 티켓 1개 생성 (재고 100개)
        Ticket ticket = Ticket.builder()
                .title("윤진석 콘서트 선착순 예매")
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
    @DisplayName("[성공 케이스] Redisson 분산 락 적용 시 100명 동시 예매 -> 전원 체결, 재고 0개 정합성 보장")
    void concurrentReservationTest() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            User currentUser = users.get(i);
            executorService.submit(() -> {
                try {
                    // Redisson Lock Facade를 통해 락 획득 후 순차 처리
                    redissonLockTicketFacade.reserveTicket(currentUser.getId(), savedTicket.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        Ticket findTicket = ticketRepository.findById(savedTicket.getId()).orElseThrow();
        long orderCount = ticketOrderRepository.count();

        System.out.println("==================================================");
        System.out.println(" [테스트 결과] Redisson 분산 락 적용");
        System.out.println(" - 총 요청 스레드 수 : " + threadCount);
        System.out.println(" - 성공한 예매 건수   : " + successCount.get());
        System.out.println(" - 충돌/실패 건수     : " + failureCount.get());
        System.out.println(" - 남은 티켓 수량     : " + findTicket.getRemainingQuantity() + " (기대값: 0, 완벽 일치)");
        System.out.println(" - DB 생성된 주문 수  : " + orderCount);
        System.out.println("==================================================");

        // 100건 모두 정상 체결 및 재고 0개 도달 검증
        assertThat(findTicket.getRemainingQuantity()).isEqualTo(0);
        assertThat(orderCount).isEqualTo(100);
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failureCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("[실패 케이스] 락 없이 100명이 동시 예매 시 갱신 분실/버전 충돌로 대다수 요청 실패 및 재고 불일치")
    void raceConditionWithoutLockTest() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 멀티스레드 환경에서 안전하게 카운트를 집계하기 위한 원자적 정수 객체
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            User currentUser = users.get(i);
            executorService.submit(() -> {
                try {
                    // 분산 락 없이 트랜잭션 서비스 직접 호출 (낙관적 락 충돌 유발)
                    ticketOrderService.createOrder(currentUser.getId(), savedTicket.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        Ticket findTicket = ticketRepository.findById(savedTicket.getId()).orElseThrow();
        long orderCount = ticketOrderRepository.count();

        System.out.println("==================================================");
        System.out.println(" [테스트 결과] 락 미적용 (순수 JPA @Version 충돌)");
        System.out.println(" - 총 요청 스레드 수 : " + threadCount);
        System.out.println(" - 성공한 예매 건수   : " + successCount.get());
        System.out.println(" - 충돌/실패 건수     : " + failureCount.get());
        System.out.println(" - 남은 티켓 수량     : " + findTicket.getRemainingQuantity() + " (기대값: 0, 불일치)");
        System.out.println(" - DB 생성된 주문 수  : " + orderCount);
        System.out.println("==================================================");

        // 100명이 주문을 시도했으나 충돌로 인해 티켓이 0이 되지 못함을 검증
        assertThat(findTicket.getRemainingQuantity()).isGreaterThan(0);
        assertThat(orderCount).isEqualTo(successCount.get());
    }

}