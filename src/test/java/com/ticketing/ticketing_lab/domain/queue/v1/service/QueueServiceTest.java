package com.ticketing.ticketing_lab.domain.queue.v1.service;

import com.ticketing.ticketing_lab.domain.queue.enums.QueueStatus;
import com.ticketing.ticketing_lab.domain.queue.v1.dto.QueueResponseDto;
import com.ticketing.ticketing_lab.domain.ticket.repository.TicketRepository;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @InjectMocks
    private QueueService queueService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Test
    @DisplayName("존재하지 않는 티켓으로 대기열 진입 시 TICKET_NOT_FOUND 예외 발생")
    void enterQueue_ticketNotFound() {
        // given
        Long ticketId = 999L;
        Long userId = 1L;
        given(ticketRepository.existsById(ticketId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> queueService.enterQueue(userId, ticketId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 Active 상태인 유저는 대기열 진입 시 즉시 ACTIVE 응답 반환")
    void enterQueue_alreadyActive() {
        // given
        Long ticketId = 1L;
        Long userId = 1L;
        given(ticketRepository.existsById(ticketId)).willReturn(true);
        given(stringRedisTemplate.hasKey("queue:ticket:1:active:1")).willReturn(true);

        // when
        QueueResponseDto response = queueService.enterQueue(userId, ticketId);

        // then
        assertThat(response.status()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(response.rank()).isEqualTo(0L);
    }

    @Test
    @DisplayName("신규 유저 대기열 진입 시 ZSET에 추가되고 순번(Rank)이 반환된다")
    void enterQueue_success() {
        // given
        Long ticketId = 1L;
        Long userId = 1L;
        given(ticketRepository.existsById(ticketId)).willReturn(true);
        given(stringRedisTemplate.hasKey("queue:ticket:1:active:1")).willReturn(false);
        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);
        given(zSetOperations.addIfAbsent(eq("queue:ticket:1:waiting"), eq("1"), anyDouble())).willReturn(true);
        given(zSetOperations.rank("queue:ticket:1:waiting", "1")).willReturn(5L);

        // when
        QueueResponseDto response = queueService.enterQueue(userId, ticketId);

        // then
        assertThat(response.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.rank()).isEqualTo(5L);
        assertThat(response.estimatedWaitTimeSec()).isGreaterThan(0L);
        verify(setOperations).add("queue:active-tickets", "1");
    }

    @Test
    @DisplayName("대기열 상태 조회 - Active 유저는 ACTIVE 상태 반환")
    void getQueueStatus_active() {
        // given
        Long ticketId = 1L;
        Long userId = 1L;
        given(stringRedisTemplate.hasKey("queue:ticket:1:active:1")).willReturn(true);

        // when
        QueueResponseDto response = queueService.getQueueStatus(userId, ticketId);

        // then
        assertThat(response.status()).isEqualTo(QueueStatus.ACTIVE);
    }

    @Test
    @DisplayName("대기열 상태 조회 - ZSET에 존재하는 유저는 WAITING 상태와 순번 반환")
    void getQueueStatus_waiting() {
        // given
        Long ticketId = 1L;
        Long userId = 1L;
        given(stringRedisTemplate.hasKey("queue:ticket:1:active:1")).willReturn(false);
        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.rank("queue:ticket:1:waiting", "1")).willReturn(12L);

        // when
        QueueResponseDto response = queueService.getQueueStatus(userId, ticketId);

        // then
        assertThat(response.status()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.rank()).isEqualTo(12L);
    }

    @Test
    @DisplayName("대기열 상태 조회 - 대기열에도 없고 Active도 아니면 NOT_IN_QUEUE 반환")
    void getQueueStatus_notInQueue() {
        // given
        Long ticketId = 1L;
        Long userId = 1L;
        given(stringRedisTemplate.hasKey("queue:ticket:1:active:1")).willReturn(false);
        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.rank("queue:ticket:1:waiting", "1")).willReturn(null);

        // when
        QueueResponseDto response = queueService.getQueueStatus(userId, ticketId);

        // then
        assertThat(response.status()).isEqualTo(QueueStatus.NOT_IN_QUEUE);
        assertThat(response.rank()).isNull();
    }

    @Test
    @DisplayName("주문 완료 시 Active 키가 삭제(토큰 소모)된다")
    void completeOrder_success() {
        // given
        Long ticketId = 1L;
        Long userId = 1L;

        // when
        queueService.completeOrder(userId, ticketId);

        // then
        verify(stringRedisTemplate).delete("queue:ticket:1:active:1");
    }

    @Test
    @DisplayName("대기열 승격 - popMin으로 추출된 유저들에게 Active 키와 TTL이 부여된다")
    void promoteWaitingUsers_success() {
        // given
        Long ticketId = 1L;
        long count = 2L;

        ZSetOperations.TypedTuple<String> tuple1 = ZSetOperations.TypedTuple.of("10", 1000.0);
        ZSetOperations.TypedTuple<String> tuple2 = ZSetOperations.TypedTuple.of("20", 2000.0);

        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.popMin("queue:ticket:1:waiting", count))
                .willReturn(Set.of(tuple1, tuple2));
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(zSetOperations.size("queue:ticket:1:waiting")).willReturn(0L);
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);

        // when
        long promoted = queueService.promoteWaitingUsers(ticketId, count);

        // then
        assertThat(promoted).isEqualTo(2L);
        verify(valueOperations).set(eq("queue:ticket:1:active:10"), eq("ACTIVE"), any(Duration.class));
        verify(valueOperations).set(eq("queue:ticket:1:active:20"), eq("ACTIVE"), any(Duration.class));
        verify(setOperations).remove("queue:active-tickets", "1");
    }

    @Test
    @DisplayName("대기열에 대기자가 없으면 0명을 반환하고 활성 목록에서 제거된다")
    void promoteWaitingUsers_emptyQueue() {
        // given
        Long ticketId = 1L;
        long count = 10L;

        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.popMin("queue:ticket:1:waiting", count))
                .willReturn(Collections.emptySet());
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);

        // when
        long promoted = queueService.promoteWaitingUsers(ticketId, count);

        // then
        assertThat(promoted).isEqualTo(0L);
        verify(setOperations).remove("queue:active-tickets", "1");
    }

    @Test
    @DisplayName("모든 활성 티켓 대기열 일괄 승격 - 활성 티켓 목록의 각 티켓에 대해 승격이 수행된다")
    void promoteAllWaitingQueues_success() {
        // given
        ZSetOperations.TypedTuple<String> tuple = ZSetOperations.TypedTuple.of("1", 1000.0);

        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.members("queue:active-tickets")).willReturn(Set.of("100"));
        given(stringRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.popMin(eq("queue:ticket:100:waiting"), anyLong()))
                .willReturn(Set.of(tuple));
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(zSetOperations.size("queue:ticket:100:waiting")).willReturn(0L);

        // when
        queueService.promoteAllWaitingQueues();

        // then
        verify(setOperations).members("queue:active-tickets");
        verify(valueOperations).set(eq("queue:ticket:100:active:1"), eq("ACTIVE"), any(Duration.class));
        verify(setOperations).remove("queue:active-tickets", "100");
    }
}
