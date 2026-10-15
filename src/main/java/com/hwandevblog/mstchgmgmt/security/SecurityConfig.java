package com.hwandevblog.mstchgmgmt.security;

import com.hwandevblog.mstchgmgmt.account.AppUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * 세션 인증과 CSRF 보호, API별 기본 접근 권한을 설정한다.
 * 요청 소유권은 서비스의 리비전 정책에서 추가로 검사한다.
 */
@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository users) {
        return username -> users.findByUsername(username).map(SessionUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppUserRepository users) throws Exception {
        // 기본 세션 CSRF 저장소를 유지하고, 화면은 조회 API가 반환한 마스킹 토큰을 전송한다.
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/api/auth/csrf", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/requests").hasRole("REQUESTER")
                .requestMatchers("/api/requests/mine").hasRole("REQUESTER")
                .requestMatchers("/api/**").hasAnyRole("REQUESTER", "APPROVER", "VIEWER")
                .anyRequest().denyAll());
        http.formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, auth) -> response.setStatus(204))
                .failureHandler((request, response, exception) -> response.sendError(401)));
        http.logout(logout -> logout.logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)));
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> response.sendError(401))
                .accessDeniedHandler((request, response, exception) -> response.sendError(403)));
        http.requestCache(cache -> cache.disable());
        http.addFilterBefore(new ActiveAccountFilter(users), AuthorizationFilter.class);
        return http.build();
    }
}
