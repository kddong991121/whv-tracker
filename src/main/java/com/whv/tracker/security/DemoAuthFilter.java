package com.whv.tracker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 데모 프로필 전용. 구글 로그인 없이 고정 가짜 사용자(DEMO_SUB)로 들어간다.
 * 실제 인증이 아니므로 운영 프로필에서는 빈이 만들어지지 않는다.
 */
@Component
@Profile("demo")
public class DemoAuthFilter extends OncePerRequestFilter {

    /** 데모 사용자의 식별자. DemoDataLoader가 이 값으로 계정 행을 만든다. 실제 구글 sub가 아니다. */
    public static final String DEMO_SUB = "demo-user";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    DEMO_SUB, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        }
        chain.doFilter(request, response);
    }
}
