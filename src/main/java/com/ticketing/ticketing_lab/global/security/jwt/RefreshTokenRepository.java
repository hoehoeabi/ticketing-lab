package com.ticketing.ticketing_lab.global.security.jwt;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {

    // Refresh Token 문자열 값으로 Redis 조회
    Optional<RefreshToken> findByToken(String token);
}
