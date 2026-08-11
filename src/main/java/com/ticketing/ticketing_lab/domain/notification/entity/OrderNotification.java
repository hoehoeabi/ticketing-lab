package com.ticketing.ticketing_lab.domain.notification.entity;

import com.ticketing.ticketing_lab.domain.notification.enums.NotificationStatus;
import com.ticketing.ticketing_lab.domain.order.entity.TicketOrder;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private TicketOrder order;

    @Column(name = "receiver_email", nullable = false)
    private String receiverEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationStatus status;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Builder
    public OrderNotification(TicketOrder order, String receiverEmail, NotificationStatus status) {
        this.order = order;
        this.receiverEmail = receiverEmail;
        this.status = (status != null) ? status : NotificationStatus.PENDING;
    }

    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }
}
