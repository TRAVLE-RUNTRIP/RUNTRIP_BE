package com.runtrip.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
public class SwaggerSecurityConfig {

    // Swagger 문서 경로는 Spring Security 필터에서 제외
    @Bean
    public WebSecurityCustomizer swaggerIgnoringCustomizer() {
        return web -> web.ignoring().requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**");
    }
}
