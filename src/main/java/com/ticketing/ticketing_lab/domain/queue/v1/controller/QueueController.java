package com.ticketing.ticketing_lab.domain.queue.v1.controller;

import com.ticketing.ticketing_lab.domain.queue.v1.dto.QueueResponseDto;
import com.ticketing.ticketing_lab.domain.queue.v1.service.QueueService;
import com.ticketing.ticketing_lab.global.common.RsData;
import com.ticketing.ticketing_lab.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    /**
     * 대기열 진입 API
     * - 해당 티켓의 대기열(ZSET)에 유저를 등록하고 현재 대기 순번 및 예상 대기 시간을 반환
     */
    @PostMapping("/tickets/{ticketId}/enter")
    public RsData<QueueResponseDto> enterQueue(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        QueueResponseDto response = queueService.enterQueue(userDetails.getUserId(), ticketId);
        return RsData.success("대기열 진입에 성공했습니다.", response);
    }

    /**
     * 대기열 상태 실시간 폴링 (Polling) API
     * - 클라이언트가 주기적으로 호출하여 자신의 현재 순번 및 활성화(Active) 여부를 확인
     */
    @GetMapping("/tickets/{ticketId}/status")
    public RsData<QueueResponseDto> getQueueStatus(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        QueueResponseDto response = queueService.getQueueStatus(userDetails.getUserId(), ticketId);
        return RsData.success("대기열 상태 조회가 완료되었습니다.", response);
    }

    /**
     * 대기열 이탈 API
     * - 유저가 대기 도중 이탈(뒤로가기/창 닫기 등) 시 대기열 ZSET에서 제거
     */
    @DeleteMapping("/tickets/{ticketId}/leave")
    public RsData<Void> leaveQueue(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        queueService.leaveQueue(userDetails.getUserId(), ticketId);
        return RsData.success("대기열에서 정상적으로 이탈했습니다.", null);
    }
}
