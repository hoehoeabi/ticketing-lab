package com.ticketing.ticketing_lab.domain.ticket.v1.controller;

import com.ticketing.ticketing_lab.domain.ticket.v1.dto.TicketCreateReqDto;
import com.ticketing.ticketing_lab.domain.ticket.v1.dto.TicketResponseDto;
import com.ticketing.ticketing_lab.domain.ticket.v1.service.TicketV1Service;
import com.ticketing.ticketing_lab.global.common.RsData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketV1Controller {

    private final TicketV1Service ticketV1Service;

    /**
     * 티켓 등록 API
     */
    @PostMapping
    public ResponseEntity<RsData<TicketResponseDto>> createTicket(@Valid @RequestBody TicketCreateReqDto reqDto) {
        TicketResponseDto responseDto = ticketV1Service.createTicket(reqDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RsData.of("201", "티켓이 성공적으로 등록되었습니다.", responseDto));
    }

    /**
     * 티켓 단건 상세 조회 API
     */
    @GetMapping("/{ticketId}")
    public RsData<TicketResponseDto> getTicket(@PathVariable Long ticketId) {
        TicketResponseDto responseDto = ticketV1Service.getTicket(ticketId);
        return RsData.of("200", "티켓 조회가 완료되었습니다.", responseDto);
    }

    /**
     * 티켓 목록 페이징 조회 API
     */
    @GetMapping
    public RsData<Page<TicketResponseDto>> getTickets(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TicketResponseDto> tickets = ticketV1Service.getTickets(pageable);
        return RsData.of("200", "티켓 목록 조회가 완료되었습니다.", tickets);
    }
}
