package com.ticketing.ticketing_lab.domain.ticket.v1.service;

import com.ticketing.ticketing_lab.domain.ticket.entity.Ticket;
import com.ticketing.ticketing_lab.domain.ticket.repository.TicketRepository;
import com.ticketing.ticketing_lab.domain.ticket.v1.dto.TicketCreateReqDto;
import com.ticketing.ticketing_lab.domain.ticket.v1.dto.TicketResponseDto;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketV1ServiceTest {

    @InjectMocks
    private TicketV1Service ticketV1Service;

    @Mock
    private TicketRepository ticketRepository;

    @Test
    @DisplayName("티켓 생성 성공 - 잔여 수량은 발행 수량과 동일하게 초기화된다")
    void createTicketSuccess() {
        // given
        LocalDateTime openAt = LocalDateTime.now().plusDays(7);
        TicketCreateReqDto reqDto = new TicketCreateReqDto("뮤지컬 지킬앤하이드", 500, openAt);

        Ticket savedTicket = Ticket.builder()
                .title("뮤지컬 지킬앤하이드")
                .totalQuantity(500)
                .remainingQuantity(500)
                .openAt(openAt)
                .build();
        given(ticketRepository.save(any(Ticket.class))).willReturn(savedTicket);

        // when
        TicketResponseDto response = ticketV1Service.createTicket(reqDto);

        // then
        assertThat(response.title()).isEqualTo("뮤지컬 지킬앤하이드");
        assertThat(response.totalQuantity()).isEqualTo(500);
        assertThat(response.remainingQuantity()).isEqualTo(500);
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    @DisplayName("티켓 단건 조회 성공")
    void getTicketSuccess() {
        // given
        LocalDateTime openAt = LocalDateTime.now().plusDays(3);
        Ticket ticket = Ticket.builder()
                .title("아이유 콘서트")
                .totalQuantity(100)
                .remainingQuantity(100)
                .openAt(openAt)
                .build();
        given(ticketRepository.findById(1L)).willReturn(Optional.of(ticket));

        // when
        TicketResponseDto response = ticketV1Service.getTicket(1L);

        // then
        assertThat(response.title()).isEqualTo("아이유 콘서트");
        assertThat(response.totalQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("티켓 단건 조회 실패 - 존재하지 않는 ID면 TICKET_NOT_FOUND 예외 발생")
    void getTicketFailNotFound() {
        // given
        given(ticketRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ticketV1Service.getTicket(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("티켓 목록 페이징 조회 성공")
    void getTicketsSuccess() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Ticket ticket = Ticket.builder()
                .title("공연 1")
                .totalQuantity(50)
                .remainingQuantity(50)
                .openAt(LocalDateTime.now())
                .build();
        Page<Ticket> page = new PageImpl<>(List.of(ticket), pageable, 1);
        given(ticketRepository.findAll(pageable)).willReturn(page);

        // when
        Page<TicketResponseDto> result = ticketV1Service.getTickets(pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("공연 1");
    }
}
