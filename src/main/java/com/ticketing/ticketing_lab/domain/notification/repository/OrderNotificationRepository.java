package com.ticketing.ticketing_lab.domain.notification.repository;

import com.ticketing.ticketing_lab.domain.notification.entity.OrderNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, Long> {

    Optional<OrderNotification> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);
}
