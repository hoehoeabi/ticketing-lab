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

    // JPQL 기반 @Query 어노테이션 메서드는 삭제! QueryDSL 구현체가 대신 동작
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
}
