package com.ticketing.ticketing_lab.domain.order.v1.controller;

import com.ticketing.ticketing_lab.domain.order.v1.dto.TicketOrderResponseDto;
import com.ticketing.ticketing_lab.domain.order.v1.service.TicketOrderService;
import com.ticketing.ticketing_lab.domain.ticket.v2.facade.RedissonLockTicketFacade;
import com.ticketing.ticketing_lab.global.common.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.format.annotation.DateTimeFormat;
import com.ticketing.ticketing_lab.global.security.user.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class TicketOrderController {

    private final TicketOrderService ticketOrderService;
    private final RedissonLockTicketFacade redissonLockTicketFacade;

    // 1. 주문 내역 No-Offset 조회
    @GetMapping
    public RsData<Slice<TicketOrderResponseDto>> getOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastCreatedAt,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "20") int size
    ) {
        Slice<TicketOrderResponseDto> result = ticketOrderService.getOrdersNoOffset(lastCreatedAt, lastId, size);
        return RsData.success("주문 목록 조회가 완료되었습니다.", result);
    }

    // 2. 선착순 예매 (Redisson Lock 적용)
    @PostMapping("/{ticketId}")
    public RsData<Long> reserveTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long orderId = redissonLockTicketFacade.reserveTicket(userDetails.getUserId(), ticketId);
        return RsData.success("티켓 예매에 성공했습니다.", orderId);
    }
}
