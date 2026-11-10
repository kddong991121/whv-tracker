package com.whv.tracker.web;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** PWA 설정 파일(manifest.webmanifest)을 올바른 형식으로 내려준다. */
@Configuration
public class PwaConfig {

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> pwaMimeTypes() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("webmanifest", "application/manifest+json");
            factory.setMimeMappings(mappings);
        };
    }
}
