package com.naengo.api_server.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * `cors.*` 설정 바인딩. 쿠키 인증과 호환되도록 `allowCredentials=true` 가 고정 정책.
 * `allowedOrigins` 는 SecurityConfig 에서 `setAllowedOriginPatterns` 로 적용되므로
 * 와일드카드 (`*` 또는 `https://*.example.com` 형태) 사용 가능.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        long maxAgeSeconds
) {
}
