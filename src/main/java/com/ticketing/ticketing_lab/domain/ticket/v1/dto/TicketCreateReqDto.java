package com.ticketing.ticketing_lab.domain.ticket.v1.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record TicketCreateReqDto(
        @NotBlank(message = "공연/티켓 제목은 필수 입력값입니다.")
        String title,

        @NotNull(message = "발행 수량은 필수 입력값입니다.")
        @Min(value = 1, message = "발행 수량은 1개 이상이어야 합니다.")
        Integer totalQuantity,

        @NotNull(message = "예매 오픈 일시는 필수 입력값입니다.")
        LocalDateTime openAt
) {
}
