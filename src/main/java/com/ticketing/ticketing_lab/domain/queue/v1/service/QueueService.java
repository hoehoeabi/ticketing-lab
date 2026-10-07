package com.ticketing.ticketing_lab.domain.queue.v1.service;

import com.ticketing.ticketing_lab.domain.queue.enums.QueueStatus;
import com.ticketing.ticketing_lab.domain.queue.v1.dto.QueueResponseDto;
import com.ticketing.ticketing_lab.domain.ticket.repository.TicketRepository;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    @Value("${queue.estimated-tps:20}")
    private long estimatedTps = 20L;

    @Value("${queue.active-ttl-minutes:5}")
    private long activeTtlMinutes = 5L;

    private final StringRedisTemplate stringRedisTemplate;
    private final TicketRepository ticketRepository;

    /**
     * 대기열 진입 (Enqueue)
     * - 이미 Active 상태라면 즉시 Active 응답
     * - 미등록 시 ZSET에 현재 타임스탬프(Score)로 등록 (중복 요청 시 기존 Score 유지)
     * - 현재 내 앞 대기 인원(Rank) 및 예상 대기 시간 반환
     */
    public QueueResponseDto enterQueue(Long userId, Long ticketId) {
        validateTicket(ticketId);

        // 이미 Active 상태인지 확인
        if (isActive(userId, ticketId)) {
            return QueueResponseDto.ofActive(userId, ticketId);
        }

        String waitingKey = getWaitingQueueKey(ticketId);
        String member = String.valueOf(userId);

        // 대기열 등록 (Score: 진입 시점 타임스탬프)
        // addIfAbsent: 이미 등록되어 있는 경우 기존 점수를 유지하여 새치기 방지
        Boolean isNew = stringRedisTemplate.opsForZSet().addIfAbsent(
                waitingKey,
                member,
                (double) System.currentTimeMillis()
        );

        // 현재 내 앞 순번(Rank) 조회 (0-based)
        Long rank = stringRedisTemplate.opsForZSet().rank(waitingKey, member);
        if (rank == null) {
            rank = 0L;
        }

        long estimatedWaitTimeSec = calculateEstimatedWaitTime(rank);
        log.info("[대기열 진입] userId: {}, ticketId: {}, rank: {} (신규: {})", userId, ticketId, rank, isNew);

        return QueueResponseDto.ofWaiting(userId, ticketId, rank, estimatedWaitTimeSec);
    }

    /**
     * 대기열 상태 조회 (Polling)
     * - Active 상태인지, 아직 대기(Waiting) 중인지, 만료/미등록인지 확인
     */
    public QueueResponseDto getQueueStatus(Long userId, Long ticketId) {
        // 이미 Active 상태인지 확인
        if (isActive(userId, ticketId)) {
            return QueueResponseDto.ofActive(userId, ticketId);
        }

        // 대기열(ZSET)에 존재하는지 확인
        String waitingKey = getWaitingQueueKey(ticketId);
        String member = String.valueOf(userId);
        Long rank = stringRedisTemplate.opsForZSet().rank(waitingKey, member);

        if (rank != null) {
            long estimatedWaitTimeSec = calculateEstimatedWaitTime(rank);
            return QueueResponseDto.ofWaiting(userId, ticketId, rank, estimatedWaitTimeSec);
        }

        // 대기열에도 없고 Active도 아님
        return QueueResponseDto.ofNotInQueue(userId, ticketId);
    }

    /**
     * 유저의 예매 접근 권한(Active 상태) 검증
     */
    public boolean isActive(Long userId, Long ticketId) {
        String activeKey = getActiveUserKey(ticketId, userId);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(activeKey));
    }

    /**
     * 예매 완료 시 Active 토큰 소모 (재사용 방지)
     */
    public void completeOrder(Long userId, Long ticketId) {
        String activeKey = getActiveUserKey(ticketId, userId);
        stringRedisTemplate.delete(activeKey);
        log.info("[대기열 토큰 소모 완료] userId: {}, ticketId: {}", userId, ticketId);
    }

    /**
     * 대기열 이탈 (자진 취소)
     */
    public void leaveQueue(Long userId, Long ticketId) {
        String waitingKey = getWaitingQueueKey(ticketId);
        stringRedisTemplate.opsForZSet().remove(waitingKey, String.valueOf(userId));
        log.info("[대기열 이탈] userId: {}, ticketId: {}", userId, ticketId);
    }

    /**
     * 대기열 상위 N명을 Active 상태로 승격 (스케줄러에서 호출)
     * - popMin을 통해 Score(타임스탬프)가 가장 낮은 앞선 대기자들을 원자적으로 추출
     * - 추출된 유저에게 ACTIVE 키 및 TTL 5분 부여
     */
    public long promoteWaitingUsers(Long ticketId, long count) {
        String waitingKey = getWaitingQueueKey(ticketId);

        Set<ZSetOperations.TypedTuple<String>> poppedUsers =
                stringRedisTemplate.opsForZSet().popMin(waitingKey, count);

        if (poppedUsers == null || poppedUsers.isEmpty()) {
            return 0L;
        }

        long promotedCount = 0;
        for (ZSetOperations.TypedTuple<String> tuple : poppedUsers) {
            String member = tuple.getValue();
            if (member != null) {
                Long userId = Long.valueOf(member);
                String activeKey = getActiveUserKey(ticketId, userId);
                stringRedisTemplate.opsForValue().set(activeKey, "ACTIVE", Duration.ofMinutes(activeTtlMinutes));
                promotedCount++;
            }
        }

        log.info("[대기열 승격] ticketId: {}, 승격 완료 인원: {}명", ticketId, promotedCount);
        return promotedCount;
    }

    /**
     * 현재 대기 중인 총 인원수 조회
     */
    public Long getWaitingQueueSize(Long ticketId) {
        String waitingKey = getWaitingQueueKey(ticketId);
        Long size = stringRedisTemplate.opsForZSet().size(waitingKey);
        return size != null ? size : 0L;
    }

    private String getWaitingQueueKey(Long ticketId) {
        return "queue:ticket:" + ticketId + ":waiting";
    }

    private String getActiveUserKey(Long ticketId, Long userId) {
        return "queue:ticket:" + ticketId + ":active:" + userId;
    }

    private void validateTicket(Long ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new BusinessException(ErrorCode.TICKET_NOT_FOUND);
        }
    }

    private long calculateEstimatedWaitTime(Long rank) {
        if (rank == null || rank == 0) {
            return 1L;
        }
        long tps = (estimatedTps > 0) ? estimatedTps : 20L;
        return (rank / tps) + 1;
    }
}
