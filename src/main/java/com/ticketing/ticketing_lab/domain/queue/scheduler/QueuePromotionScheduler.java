package com.ticketing.ticketing_lab.domain.queue.scheduler;

import com.ticketing.ticketing_lab.domain.queue.v1.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueuePromotionScheduler {

    private final QueueService queueService;

    /**
     * 주기적으로 대기열의 상위 인원을 Active 상태로 승격
     * - 기본 주기: 1000ms (1초마다 실행)
     * - application.yml의 queue.promotion.interval-ms 값으로 주기 제어 가능
     */
    @Scheduled(fixedRateString = "${queue.promotion.interval-ms:1000}")
    public void schedulePromotion() {
        queueService.promoteAllWaitingQueues();
    }
}
