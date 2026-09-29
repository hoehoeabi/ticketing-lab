package com.ticketing.ticketing_lab.domain.order.v1.service;

import com.ticketing.ticketing_lab.domain.order.entity.TicketOrder;
import com.ticketing.ticketing_lab.domain.order.enums.OrderStatus;
import com.ticketing.ticketing_lab.domain.order.repository.TicketOrderRepository;
import com.ticketing.ticketing_lab.domain.order.v1.dto.TicketOrderResponseDto;
import com.ticketing.ticketing_lab.domain.ticket.entity.Ticket;
import com.ticketing.ticketing_lab.domain.ticket.repository.TicketRepository;
import com.ticketing.ticketing_lab.domain.user.entity.User;
import com.ticketing.ticketing_lab.domain.user.enums.Role;
import com.ticketing.ticketing_lab.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketOrderServiceTest {

    @InjectMocks
    private TicketOrderService ticketOrderService;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("[대조군] Offset 기반 주문 목록 조회 - 페이징 결과가 정상 반환된다")
    void getOrdersOffset_success() {
        // given
        User user = User.builder()
                .email("test@test.com")
                .password("pwd")
                .role(Role.ROLE_USER)
                .provider("LOCAL")
                .build();

        Ticket ticket = Ticket.builder()
                .title("콘서트")
                .totalQuantity(100)
                .remainingQuantity(100)
                .openAt(LocalDateTime.now())
                .build();

        TicketOrder order = TicketOrder.builder()
                .user(user)
                .ticket(ticket)
                .status(OrderStatus.SUCCESS)
                .build();

        Page<TicketOrder> orderPage = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
        given(ticketOrderRepository.findOrdersOffset(any(PageRequest.class))).willReturn(orderPage);

        // when
        Page<TicketOrderResponseDto> result = ticketOrderService.getOrdersOffset(0, 20);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserEmail()).isEqualTo("test@test.com");
        assertThat(result.getContent().get(0).getTicketTitle()).isEqualTo("콘서트");
        verify(ticketOrderRepository).findOrdersOffset(any(PageRequest.class));
    }

    @Test
    @DisplayName("[최적화] No-Offset 커서 기반 주문 목록 조회 - 커서 기반 슬라이스 결과가 정상 반환된다")
    void getOrdersNoOffset_success() {
        // given
        User user = User.builder()
                .email("test@test.com")
                .password("pwd")
                .role(Role.ROLE_USER)
                .provider("LOCAL")
                .build();

        Ticket ticket = Ticket.builder()
                .title("콘서트")
                .totalQuantity(100)
                .remainingQuantity(100)
                .openAt(LocalDateTime.now())
                .build();

        TicketOrder order = TicketOrder.builder()
                .user(user)
                .ticket(ticket)
                .status(OrderStatus.SUCCESS)
                .build();

        Slice<TicketOrder> orderSlice = new SliceImpl<>(List.of(order));
        LocalDateTime now = LocalDateTime.now();
        given(ticketOrderRepository.findOrdersNoOffset(eq(now), eq(10L), any(PageRequest.class))).willReturn(orderSlice);

        // when
        Slice<TicketOrderResponseDto> result = ticketOrderService.getOrdersNoOffset(now, 10L, 20);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserEmail()).isEqualTo("test@test.com");
        verify(ticketOrderRepository).findOrdersNoOffset(eq(now), eq(10L), any(PageRequest.class));
    }
}
