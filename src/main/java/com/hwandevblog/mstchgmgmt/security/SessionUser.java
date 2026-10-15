package com.hwandevblog.mstchgmgmt.security;

import com.hwandevblog.mstchgmgmt.account.AppUser;
import com.hwandevblog.mstchgmgmt.domain.RevisionPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.Set;

/**
 * 인증된 세션에 저장하는 사용자 정보를 표현한다.
 * 요청자와 승인자 ID는 입력값 대신 이 인증 정보에서 가져온다.
 */
public record SessionUser(long id, String username, String passwordHash, boolean active,
                          Set<RevisionPolicy.Role> roles) implements UserDetails {
    @Serial private static final long serialVersionUID = 1L;

    public SessionUser { roles = Set.copyOf(roles); }

    public static SessionUser from(AppUser user) {
        return new SessionUser(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.isActive(), user.getRoles());
    }

    public RevisionPolicy.Actor actor() { return new RevisionPolicy.Actor(id, active, roles); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return active; }
}
