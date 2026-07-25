package com.zeroverse.security;

import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 인증된 사용자 정보. {@code SecurityContext}의 principal로 들어간다.
 *
 * <p>비밀번호 해시를 담지 않는다 — 인증 이후에는 필요 없고, 실수로 응답·로그에 흘릴 위험만 남는다.
 */
public record ZeroverseUserPrincipal(Long userId, UserRole role) {

    public static ZeroverseUserPrincipal from(User user) {
        return new ZeroverseUserPrincipal(user.getId(), user.getRole());
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }
}
