package com.ticketing.ticketing_lab.domain.ticket.v1.dto;

import com.ticketing.ticketing_lab.domain.ticket.entity.Ticket;

import java.time.LocalDateTime;

public record TicketResponseDto(
        Long id,
        String title,
        Integer totalQuantity,
        Integer remainingQuantity,
        LocalDateTime openAt,
        LocalDateTime createdAt
) {
    public static TicketResponseDto from(Ticket ticket) {
        return new TicketResponseDto(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getTotalQuantity(),
                ticket.getRemainingQuantity(),
                ticket.getOpenAt(),
                ticket.getCreatedAt()
        );
    }
}
