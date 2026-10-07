package com.ticketing.ticketing_lab.domain.queue.enums;

public enum QueueStatus {
    WAITING,       // 대기 중 (순번 대기)
    ACTIVE,        // 활성화됨 (예매 가능 상태)
    NOT_IN_QUEUE   // 대기열에 없거나 만료됨
}
