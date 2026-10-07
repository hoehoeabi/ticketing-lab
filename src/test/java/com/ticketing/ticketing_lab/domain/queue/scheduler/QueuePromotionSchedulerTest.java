package com.ticketing.ticketing_lab.domain.queue.scheduler;

import com.ticketing.ticketing_lab.domain.queue.v1.service.QueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueuePromotionSchedulerTest {

    @InjectMocks
    private QueuePromotionScheduler scheduler;

    @Mock
    private QueueService queueService;

    @Test
    @DisplayName("스케줄러 주기 실행 시 queueService.promoteAllWaitingQueues가 호출된다")
    void schedulePromotion_callsService() {
        // when
        scheduler.schedulePromotion();

        // then
        verify(queueService).promoteAllWaitingQueues();
    }
}
