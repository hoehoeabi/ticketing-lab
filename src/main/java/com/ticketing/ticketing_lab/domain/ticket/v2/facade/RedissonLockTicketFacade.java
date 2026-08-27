package com.ticketing.ticketing_lab.domain.ticket.v2.facade;

import com.ticketing.ticketing_lab.domain.order.v1.service.TicketOrderService;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonLockTicketFacade {

    private final RedissonClient redissonClient;
    private final TicketOrderService ticketOrderService;

    public Long reserveTicket(Long userId, Long ticketId) {
        String lockKey = "lock:ticket:" + ticketId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(5, 3, TimeUnit.SECONDS);

            if (!isLocked) {
                log.warn("[Redisson Lock] 락 획득 실패 - ticketId: {}", ticketId);
                throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
            }

            return ticketOrderService.createOrder(userId, ticketId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Redisson Lock] 락 획득 중 인터럽트 발생", e);
            throw new BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
