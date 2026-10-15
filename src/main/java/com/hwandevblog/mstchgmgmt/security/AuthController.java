package com.hwandevblog.mstchgmgmt.security;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record CsrfResponse(String headerName, String token) { }
    public record UserResponse(long id, String username, Set<String> roles) { }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal SessionUser user) {
        return new UserResponse(user.id(), user.username(),
                user.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
    }
}
