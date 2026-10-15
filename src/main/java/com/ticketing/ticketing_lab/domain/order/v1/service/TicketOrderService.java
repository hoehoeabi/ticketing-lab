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
    /**
     * [선착순 티켓 예매 쓰기 트랜잭션]
     * 1. RedissonLockTicketFacade에서 Redis 분산 락을 먼저 획득한 후 이 메서드로
     진입. (동시 진입 1건으로 직렬화)
     * 2. findById 조회 시 Ticket 엔티티의 @Version 컬럼을 함께 읽어와 낙관적
     락(Optimistic Lock) 버전을 확보.
     * 3. 재고 차감(UPDATE) 및 주문 생성(INSERT) 후 트랜잭션 커밋(디스크 I/O)이
     완료되면 분산 락이 해제.
     */
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
