package com.ticketing.ticketing_lab.domain.order.repository;

import com.ticketing.ticketing_lab.domain.order.entity.TicketOrder;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
//,TicketOrderRepositoryCustom
public interface TicketOrderRepository extends JpaRepository<TicketOrder, Long> {

    /**
     * N+1 문제 방지를 위한 Fetch Join + No-Offset 커서 페이징
     * 첫 페이지 요청 시 커서 값이 null이므로 IS NULL 체크 포함
     */
    @Query("SELECT o FROM TicketOrder o " +
            "JOIN FETCH o.user " +
            "JOIN FETCH o.ticket " +
            "WHERE :lastCreatedAt IS NULL " +
            "   OR o.createdAt < :lastCreatedAt " +
            "   OR (o.createdAt = :lastCreatedAt AND o.id < :lastId) " +
            "ORDER BY o.createdAt DESC, o.id DESC")
    Slice<TicketOrder> findOrdersNoOffset(
            @Param("lastCreatedAt") LocalDateTime lastCreatedAt,
            @Param("lastId") Long lastId,
            Pageable pageable
    );

    /**
     * [대조군] 전통적인 Offset 기반 페이징 (Fetch Join 적용)
     * 100만 건 대용량 환경에서 뒤쪽 페이지 조회 시 Full Table Scan 지연 비교용
     */
    @Query(value = "SELECT o FROM TicketOrder o " +
            "JOIN FETCH o.user " +
            "JOIN FETCH o.ticket " +
            "ORDER BY o.createdAt DESC, o.id DESC",
            countQuery = "SELECT count(o) FROM TicketOrder o")
    org.springframework.data.domain.Page<TicketOrder> findOrdersOffset(Pageable pageable);
}
