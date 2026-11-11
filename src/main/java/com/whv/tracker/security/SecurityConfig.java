package com.whv.tracker.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * 구글 로그인 전용 보안 설정.
 * 공개: 홈("/"), 로그인 페이지("/login"), 오류 페이지, 인정 지역 확인("/check"), 구글 로그인 시작·콜백 경로.
 * 나머지는 모두 로그인 필요. CSRF 보호는 기본값 (켜짐) 그대로 둔다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   OAuth2LoginSuccessHandler loginSuccess,
                                                   ObjectProvider<DemoAuthFilter> demoAuth) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/error", "/check", "/privacy").permitAll()
                        // PWA 정적 파일 (아이콘, 설정, 서비스 워커, 연결 없음 화면)
                        .requestMatchers("/manifest.webmanifest", "/sw.js", "/offline.html", "/icons/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .successHandler(loginSuccess))
                .logout(logout -> logout.logoutSuccessUrl("/"));

        // 데모 프로필에서만 고정 가짜 사용자를 자동으로 로그인 상태로 만든다.
        demoAuth.ifAvailable(filter -> http.addFilterBefore(filter, AnonymousAuthenticationFilter.class));
        return http.build();
    }
}
