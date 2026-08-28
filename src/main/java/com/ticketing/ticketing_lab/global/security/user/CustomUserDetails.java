package com.ticketing.ticketing_lab.global.security.user;

import com.ticketing.ticketing_lab.domain.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        // Role enum 값(예: ROLE_USER, ROLE_ADMIN)을 SimpleGrantedAuthority로 변환
        this.authorities = Collections.singletonList(
                new SimpleGrantedAuthority(user.getRole().name())
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        // 시큐리티의 username 식별자로 email을 사용합니다.
        return email;
    }

    // 계정 만료 여부. (예: 유료 구독 기간이 끝난 계정, 1년 이상 미접속 휴면 계정)
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // 계정 잠금 여부. (예: 비밀번호 5회 틀려서 30분간 잠긴 계정)
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // 비밀번호 만료 여부. (예: 비밀번호 변경 후 90일이 지나 변경 캠페인을 띄워야 하는 계정)
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // 계정 활성화 여부. (예: 관리자가 영구 정지/밴 처리한 계정, 이메일 인증을 안 한 계정)
    /*
    JWT는 무상태(Stateless)이기 때문에, 토큰이 일단 발급되면 관리자가 DB에서 isEnabled = false로 밴을 때려도
    이미 발급된 Access Token은 만료될 때까지 계속 작동.
    따라서 즉각적인 밴이 필요하다면 Redis Blacklist를 필터에 추가.
     */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
