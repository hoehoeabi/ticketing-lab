package com.ticketing.ticketing_lab.domain.ticket.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    @Version // JPA 낙관적 락(Optimistic Lock) 버저닝
    @Column(nullable = false)
    private Long version = 0L;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Ticket(String title, Integer totalQuantity, Integer remainingQuantity, LocalDateTime openAt) {
        this.title = title;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = remainingQuantity;
        this.openAt = openAt;
    }

    // 재고 차감 비즈니스 로직
    public void decreaseQuantity(int quantity) {
        if (this.remainingQuantity < quantity) {
            throw new IllegalArgumentException("재고가 부족합니다.");
        }
        this.remainingQuantity -= quantity;
    }
}
