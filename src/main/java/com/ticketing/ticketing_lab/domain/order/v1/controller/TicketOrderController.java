package com.ticketing.ticketing_lab.domain.order.v1.controller;

import com.ticketing.ticketing_lab.domain.order.v1.dto.TicketOrderResponseDto;
import com.ticketing.ticketing_lab.domain.order.v1.service.TicketOrderService;
import com.ticketing.ticketing_lab.domain.ticket.v2.facade.RedissonLockTicketFacade;
import com.ticketing.ticketing_lab.global.common.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    // 1. 주문 내역 No-Offset 조회 (최적화 버전)
    @GetMapping
    public RsData<Slice<TicketOrderResponseDto>> getOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastCreatedAt,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "20") int size
    ) {
        Slice<TicketOrderResponseDto> result = ticketOrderService.getOrdersNoOffset(lastCreatedAt, lastId, size);
        return RsData.success("주문 목록 조회가 완료되었습니다.", result);
    }

    // 1-1. [대조군] 주문 내역 Offset 기반 페이징 조회 (100만 건 환경 비교용)
    @GetMapping("/offset")
    public RsData<Page<TicketOrderResponseDto>> getOrdersOffset(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<TicketOrderResponseDto> result = ticketOrderService.getOrdersOffset(page, size);
        return RsData.success("주문 목록(Offset) 조회가 완료되었습니다.", result);
    }

    // 2. 선착순 예매 (Redisson Lock 적용 - 최적화 버전)
    @PostMapping("/{ticketId}")
    public RsData<Long> reserveTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long orderId = redissonLockTicketFacade.reserveTicket(userDetails.getUserId(), ticketId);
        return RsData.success("티켓 예매에 성공했습니다.", orderId);
    }

    // 2-1. [대조군] 선착순 예매 (분산 락 미적용 - 동시성 충돌 비교용)
    @PostMapping("/{ticketId}/no-lock")
    public RsData<Long> reserveTicketWithoutLock(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long orderId = ticketOrderService.createOrder(userDetails.getUserId(), ticketId);
        return RsData.success("[락 미적용] 티켓 예매에 성공했습니다.", orderId);
    }
}
