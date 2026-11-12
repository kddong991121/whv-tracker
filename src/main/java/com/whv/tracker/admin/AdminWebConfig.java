package com.whv.tracker.admin;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 화면 요청마다 LastSeenInterceptor를 거친다 (정적 파일·PWA 파일은 제외). */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final LastSeenInterceptor lastSeen;

    public AdminWebConfig(LastSeenInterceptor lastSeen) {
        this.lastSeen = lastSeen;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(lastSeen).excludePathPatterns("/webjars/**", "/icons/**", "/sw.js",
                "/manifest.webmanifest", "/offline.html", "/error");
    }
}
