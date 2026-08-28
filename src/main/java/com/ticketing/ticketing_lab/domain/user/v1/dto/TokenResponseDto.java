package com.ticketing.ticketing_lab.domain.user.v1.dto;

import lombok.Builder;
import lombok.Getter;

public record TokenResponseDto(
        String accessToken,
        String refreshToken
) {
}
