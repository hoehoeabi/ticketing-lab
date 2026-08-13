package com.ticketing.ticketing_lab.global.security.jwt;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

@Getter
@RedisHash(value = "refreshToken")
public class RefreshToken {

    @Id
    private Long userId; // Key: 유저 PK ID

    @Indexed
    private String token; // Value: Refresh Token 값 (조회용 인덱스 설정)

    @TimeToLive
    private Long ttl; // 만료 시간 (초 단위)

    @Builder
    public RefreshToken(Long userId, String token, Long ttl) {
        this.userId = userId;
        this.token = token;
        this.ttl = ttl;
    }

    // RTR: 토큰이 교체될 때 값 업데이트
    public void updateToken(String newToken, Long ttl) {
        this.token = newToken;
        this.ttl = ttl;
    }
}
