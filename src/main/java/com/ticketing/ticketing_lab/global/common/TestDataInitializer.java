package com.ticketing.ticketing_lab.global.common;

import com.ticketing.ticketing_lab.domain.ticket.entity.Ticket;
import com.ticketing.ticketing_lab.domain.ticket.repository.TicketRepository;
import com.ticketing.ticketing_lab.domain.user.entity.User;
import com.ticketing.ticketing_lab.domain.user.enums.Role;
import com.ticketing.ticketing_lab.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        initTestUsers();
        initTestTickets();
    }

    private void initTestUsers() {
        List<String> testEmails = List.of(
                "user1@test.com",
                "user2@test.com"
        );

        String encodedPassword = passwordEncoder.encode("password123!");

        for (String email : testEmails) {
            if (!userRepository.existsByEmail(email)) {
                User user = User.builder()
                        .email(email)
                        .password(encodedPassword)
                        .provider("LOCAL")
                        .role(Role.ROLE_USER)
                        .build();
                userRepository.save(user);
                log.info("초기 테스트 계정 생성 완료: {}", email);
            }
        }
    }

    private void initTestTickets() {
        if (ticketRepository.count() == 0) {
            List<Ticket> sampleTickets = List.of(
                    Ticket.builder()
                            .title("2026 강산에 연어쇼 - 서울")
                            .totalQuantity(100)
                            .remainingQuantity(100)
                            .openAt(LocalDateTime.now())
                            .build(),
                    Ticket.builder()
                            .title("리센느 2026 콘서트 ")
                            .totalQuantity(50)
                            .remainingQuantity(50)
                            .openAt(LocalDateTime.now())
                            .build(),
                    Ticket.builder()
                            .title("콜드플레이 내한 공연 ")
                            .totalQuantity(200)
                            .remainingQuantity(200)
                            .openAt(LocalDateTime.now())
                            .build(),
                    Ticket.builder()
                            .title("임영웅 IM HERO 전국투어 콘서트")
                            .totalQuantity(30)
                            .remainingQuantity(30)
                            .openAt(LocalDateTime.now())
                            .build()
            );

            ticketRepository.saveAll(sampleTickets);
            log.info("초기 테스트 티켓 {}개 생성 완료", sampleTickets.size());
        }
    }
}
