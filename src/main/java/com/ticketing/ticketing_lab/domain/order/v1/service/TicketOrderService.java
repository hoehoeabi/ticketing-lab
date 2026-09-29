package com.ticketing.ticketing_lab.domain.order.v1.service;

import com.ticketing.ticketing_lab.domain.order.entity.TicketOrder;
import com.ticketing.ticketing_lab.domain.order.enums.OrderStatus;
import com.ticketing.ticketing_lab.domain.order.repository.TicketOrderRepository;
import com.ticketing.ticketing_lab.domain.order.v1.dto.TicketOrderResponseDto;
import com.ticketing.ticketing_lab.domain.ticket.entity.Ticket;
import com.ticketing.ticketing_lab.domain.ticket.repository.TicketRepository;
import com.ticketing.ticketing_lab.domain.user.entity.User;
import com.ticketing.ticketing_lab.domain.user.repository.UserRepository;
import com.ticketing.ticketing_lab.domain.order.event.OrderCreatedEvent;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TicketOrderService {

    private final TicketOrderRepository ticketOrderRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 대용량 조회 최적화 로직 (No-Offset)
    @Transactional(readOnly = true)
    public Slice<TicketOrderResponseDto> getOrdersNoOffset(LocalDateTime lastCreatedAt, Long lastId, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);
        return ticketOrderRepository.findOrdersNoOffset(lastCreatedAt, lastId, pageRequest)
                .map(TicketOrderResponseDto::from);
    }

    // [대조군]  Offset 기반 페이징 (100만 건 환경에서 Full Table Scan 지연 비교용)
    @Transactional(readOnly = true)
    public Page<TicketOrderResponseDto> getOrdersOffset(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return ticketOrderRepository.findOrdersOffset(pageRequest)
                .map(TicketOrderResponseDto::from);
    }

    // 예매 트랜잭션 (Redisson Facade 안에서 호출됨)
    @Transactional
    public Long createOrder(Long userId, Long ticketId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));

        try {
            ticket.decreaseQuantity(1);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TICKET_SOLD_OUT);
        }

        TicketOrder order = TicketOrder.builder()
                .user(user)
                .ticket(ticket)
                .status(OrderStatus.SUCCESS)
                .build();

        ticketOrderRepository.save(order);

        // 비동기 알림 및 이메일 발송을 위한 이벤트 발행
        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId(),
                user.getId(),
                user.getEmail(),
                ticket.getTitle()
        ));

        return order.getId();
    }
}
