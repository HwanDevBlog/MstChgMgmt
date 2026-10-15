package com.hwandevblog.mstchgmgmt.account;

import com.hwandevblog.mstchgmgmt.domain.RevisionPolicy.Role;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/**
 * 초기 계정의 로그인 정보와 역할을 보관한다.
 * 비밀번호 해시는 API 응답에 포함하지 않고 인증 과정에서만 사용한다.
 */
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    private Long id;
    @Column(nullable = false)
    private String username;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(nullable = false)
    private boolean active;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    protected AppUser() { }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public Set<Role> getRoles() { return Set.copyOf(roles); }
}
