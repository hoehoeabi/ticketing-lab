package com.ticketing.ticketing_lab.domain.ticket.v1.service;

import com.ticketing.ticketing_lab.domain.ticket.entity.Ticket;
import com.ticketing.ticketing_lab.domain.ticket.repository.TicketRepository;
import com.ticketing.ticketing_lab.domain.ticket.v1.dto.TicketCreateReqDto;
import com.ticketing.ticketing_lab.domain.ticket.v1.dto.TicketResponseDto;
import com.ticketing.ticketing_lab.global.error.BusinessException;
import com.ticketing.ticketing_lab.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketV1Service {

    private final TicketRepository ticketRepository;

    /**
     * 티켓 등록 (생성)
     */
    @Transactional
    public TicketResponseDto createTicket(TicketCreateReqDto reqDto) {
        Ticket ticket = Ticket.builder()
                .title(reqDto.title())
                .totalQuantity(reqDto.totalQuantity())
                .remainingQuantity(reqDto.totalQuantity()) // 신규 등록 시 잔여 수량 = 전체 수량
                .openAt(reqDto.openAt())
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("[티켓 생성 완료] ticketId: {}, title: {}, totalQuantity: {}",
                savedTicket.getId(), savedTicket.getTitle(), savedTicket.getTotalQuantity());

        return TicketResponseDto.from(savedTicket);
    }

    /**
     * 티켓 단건 상세 조회
     */
    @Transactional(readOnly = true)
    public TicketResponseDto getTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));

        return TicketResponseDto.from(ticket);
    }

    /**
     * 티켓 목록 페이징 조회
     */
    @Transactional(readOnly = true)
    public Page<TicketResponseDto> getTickets(Pageable pageable) {
        return ticketRepository.findAll(pageable)
                .map(TicketResponseDto::from);
    }
}
