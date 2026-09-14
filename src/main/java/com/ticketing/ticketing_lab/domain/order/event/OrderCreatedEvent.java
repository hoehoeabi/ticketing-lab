package com.ticketing.ticketing_lab.domain.order.event;

public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        String receiverEmail,
        String ticketTitle
) {
}
