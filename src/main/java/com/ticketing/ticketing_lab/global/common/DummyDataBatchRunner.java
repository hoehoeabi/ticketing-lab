package com.ticketing.ticketing_lab.global.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@Profile("dummy-load")
@RequiredArgsConstructor
public class DummyDataBatchRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private static final int BATCH_SIZE = 5000;
    private static final int TOTAL_ORDERS = 1_000_000;
    private static final int USER_COUNT = 10_000;
    private static final int TICKET_COUNT = 100;
    private final Random random = new Random();

    @Override
    public void run(String... args) {
        log.info("========== [더미 데이터 적재 시작] ==========");
        long startTime = System.currentTimeMillis();

        insertUsers(USER_COUNT);
        insertTickets(TICKET_COUNT);
        insertOrders(TOTAL_ORDERS);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("========== [더미 데이터 적재 완료] 총 소요시간: {} ms (약 {}초) ==========",
                totalTime, totalTime / 1000);
    }

    private void insertUsers(int count) {
        String sql = "INSERT INTO users (email, password, provider, role, created_at, updated_at) " +
                "VALUES (?, ?, 'LOCAL', 'ROLE_USER', NOW(), NOW())";
        List<Object[]> batchArgs = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            batchArgs.add(new Object[]{"user" + i + "@test.com", "$2a$10$dummyHashPassword"});
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
        log.info("[Users] {}건 적재 완료", count);
    }

    private void insertTickets(int count) {
        String sql = "INSERT INTO tickets (title, total_quantity, remaining_quantity, open_at, version, created_at) " +
                "VALUES (?, 1000, 1000, NOW(), 0, NOW())";
        List<Object[]> batchArgs = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            batchArgs.add(new Object[]{"티켓 공연 #" + i});
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
        log.info("[Tickets] {}건 적재 완료", count);
    }

    private void insertOrders(int total) {
        String orderSql = "INSERT INTO ticket_orders (user_id, ticket_id, status, created_at) VALUES (?, ?, 'SUCCESS', ?)";

        for (int i = 0; i < total; i += BATCH_SIZE) {
            final int currentBatchSize = Math.min(BATCH_SIZE, total - i);

            jdbcTemplate.batchUpdate(orderSql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int idx) throws SQLException {
                    ps.setLong(1, random.nextInt(USER_COUNT) + 1);
                    ps.setLong(2, random.nextInt(TICKET_COUNT) + 1);
                    // 최근 30일(2,592,000초) 이내의 랜덤 생성일시 부여
                    LocalDateTime randomDate = LocalDateTime.now().minusSeconds(random.nextInt(2_592_000));
                    ps.setTimestamp(3, Timestamp.valueOf(randomDate));
                }

                @Override
                public int getBatchSize() {
                    return currentBatchSize;
                }
            });

            if ((i + currentBatchSize) % 50_000 == 0 || (i + currentBatchSize) == total) {
                log.info("[TicketOrders] {} / {} 건 적재 진행 중...", (i + currentBatchSize), total);
            }
        }
    }
}
