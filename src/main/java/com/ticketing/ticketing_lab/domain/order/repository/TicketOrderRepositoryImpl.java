package com.ticketing.ticketing_lab.domain.order.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticketing.ticketing_lab.domain.order.entity.TicketOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.ticketing.ticketing_lab.domain.order.entity.QTicketOrder.ticketOrder;
import static com.ticketing.ticketing_lab.domain.ticket.entity.QTicket.ticket;
import static com.ticketing.ticketing_lab.domain.user.entity.QUser.user;

@Repository
@RequiredArgsConstructor
public class TicketOrderRepositoryImpl implements TicketOrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Slice<TicketOrder> findOrdersNoOffset(LocalDateTime lastCreatedAt, Long lastId, Pageable pageable) {

        // 1. 데이터 조회 (hasNext 확인을 위해 요청된 size 보다 1개 더 조회)
        int pageSize = pageable.getPageSize();
        List<TicketOrder> orders = queryFactory
                .selectFrom(ticketOrder)
                .join(ticketOrder.user, user).fetchJoin()     // N+1 해결: User Fetch Join
                .join(ticketOrder.ticket, ticket).fetchJoin() // N+1 해결: Ticket Fetch Join
                .where(cursorCondition(lastCreatedAt, lastId)) // 동적 쿼리 적용
                .orderBy(ticketOrder.createdAt.desc(), ticketOrder.id.desc()) // 최신순 정렬
                .limit(pageSize + 1) // 다음 페이지 여부 확인을 위해 +1
                .fetch();

        // 2. 다음 페이지(hasNext) 유무 판단
        boolean hasNext = false;
        if (orders.size() > pageSize) {
            hasNext = true;
            orders.remove(pageSize); // 화면에 돌려줄 때는 +1 했던 마지막 요소는 제거
        }

        return new SliceImpl<>(orders, pageable, hasNext);
    }

    /**
     * 커서 조건 (동적 쿼리)
     * - 첫 페이지 요청(null) 시 조건 무시 (WHERE 절 생략)
     * - 두 번째 페이지부터 복합 인덱스 (created_at, id) 적용
     */
    private BooleanExpression cursorCondition(LocalDateTime lastCreatedAt, Long lastId) {
        if (lastCreatedAt == null || lastId == null) {
            return null; // 조건이 null로 반환되면 where 절에서 자동으로 무시됩니다.
        }

        // created_at < lastCreatedAt OR (created_at == lastCreatedAt AND id < lastId)
        return ticketOrder.createdAt.lt(lastCreatedAt)
                .or(
                        ticketOrder.createdAt.eq(lastCreatedAt)
                                .and(ticketOrder.id.lt(lastId))
                );
    }
}
