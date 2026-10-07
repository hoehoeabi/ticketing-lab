package com.ticketing.ticketing_lab.domain.queue.v1.dto;

import com.ticketing.ticketing_lab.domain.queue.enums.QueueStatus;

public record QueueResponseDto(
        Long userId,
        Long ticketId,
        QueueStatus status,
        Long rank,
        Long estimatedWaitTimeSec
) {
    public static QueueResponseDto ofWaiting(Long userId, Long ticketId, Long rank, Long estimatedWaitTimeSec) {
        return new QueueResponseDto(userId, ticketId, QueueStatus.WAITING, rank, estimatedWaitTimeSec);
    }

    public static QueueResponseDto ofActive(Long userId, Long ticketId) {
        return new QueueResponseDto(userId, ticketId, QueueStatus.ACTIVE, 0L, 0L);
    }

    public static QueueResponseDto ofNotInQueue(Long userId, Long ticketId) {
        return new QueueResponseDto(userId, ticketId, QueueStatus.NOT_IN_QUEUE, null, null);
    }
}
