package com.ticketing.ticketing_lab.domain.order.v1.service;

import com.ticketing.ticketing_lab.domain.order.entity.TicketOrder;
import com.ticketing.ticketing_lab.domain.order.enums.OrderStatus;
import com.ticketing.ticketing_lab.domain.order.repository.TicketOrderRepository;
import com.ticketing.ticketing_lab.domain.order.v1.dto.TicketOrderResponseDto;
import com.ticketing.ticketing_lab.domain.ticket.entity.Ticket;
import com.ticketing.ticketing_lab.domain.ticket.repository.TicketRepository;
import com.ticketing.ticketing_lab.domain.user.entity.User;
import com.ticketing.ticketing_lab.domain.user.repository.UserRepository;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
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

    // 대용량 조회 최적화 로직 (No-Offset)
    @Transactional(readOnly = true)
    public Slice<TicketOrderResponseDto> getOrdersNoOffset(LocalDateTime lastCreatedAt, Long lastId, int size) {
        PageRequest pageRequest = PageRequest.of(0, size);
        return ticketOrderRepository.findOrdersNoOffset(lastCreatedAt, lastId, pageRequest)
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

        return order.getId();
    }
}
