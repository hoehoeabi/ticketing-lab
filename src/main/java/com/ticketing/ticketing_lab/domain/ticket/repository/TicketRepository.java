package com.ticketing.ticketing_lab.domain.ticket.repository;

import com.ticketing.ticketing_lab.domain.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
}
