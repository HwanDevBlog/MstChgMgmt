package com.hwandevblog.mstchgmgmt.security;

import com.hwandevblog.mstchgmgmt.account.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 로그인 뒤 변경된 계정 활성 여부와 역할을 API 권한 검사 전에 다시 확인한다.
 * 세션의 과거 역할로 권한이 유지되지 않도록 인증 정보를 갱신한다.
 */
public class ActiveAccountFilter extends OncePerRequestFilter {
    private final AppUserRepository users;

    public ActiveAccountFilter(AppUserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (request.getRequestURI().startsWith("/api/") && authentication != null
                && authentication.getPrincipal() instanceof SessionUser previous) {
            var current = users.findById(previous.id());
            if (current.isEmpty() || !current.get().isActive()) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                response.sendError(401);
                return;
            }
            var principal = SessionUser.from(current.get());
            var refreshed = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, principal.getAuthorities());
            refreshed.setDetails(authentication.getDetails());
            // 다른 요청이 참조하는 세션의 인증 컨텍스트를 직접 수정하지 않는다.
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(refreshed);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
