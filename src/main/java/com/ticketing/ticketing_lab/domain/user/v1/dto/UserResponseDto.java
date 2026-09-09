package com.ticketing.ticketing_lab.domain.user.v1.dto;

import com.ticketing.ticketing_lab.domain.user.entity.User;
import com.ticketing.ticketing_lab.domain.user.enums.Role;

import java.time.LocalDateTime;

public record UserResponseDto(
        Long id,
        String email,
        String provider,
        Role role,
        LocalDateTime createdAt
) {
    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getProvider(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
