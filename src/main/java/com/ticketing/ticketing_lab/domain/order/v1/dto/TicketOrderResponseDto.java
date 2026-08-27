package com.ticketing.ticketing_lab.domain.order.v1.dto;

import com.ticketing.ticketing_lab.domain.order.entity.TicketOrder;
import com.ticketing.ticketing_lab.domain.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class TicketOrderResponseDto {
    private Long orderId;
    private String userEmail;
    private String ticketTitle;
    private OrderStatus status;
    private LocalDateTime createdAt;

    public static TicketOrderResponseDto from(TicketOrder order) {
        return new TicketOrderResponseDto(
                order.getId(),
                order.getUser().getEmail(),   // Fetch Join으로 인해 추가 쿼리 발생 안 함
                order.getTicket().getTitle(), // Fetch Join으로 인해 추가 쿼리 발생 안 함
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
