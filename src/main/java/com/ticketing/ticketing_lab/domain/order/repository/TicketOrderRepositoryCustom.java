package com.ticketing.ticketing_lab.domain.order.repository;

import com.ticketing.ticketing_lab.domain.order.entity.TicketOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.time.LocalDateTime;

public interface TicketOrderRepositoryCustom {
    Slice<TicketOrder> findOrdersNoOffset(LocalDateTime lastCreatedAt, Long lastId, Pageable pageable);
}
