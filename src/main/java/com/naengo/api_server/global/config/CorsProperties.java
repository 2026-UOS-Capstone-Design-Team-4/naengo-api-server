package com.naengo.api_server.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * `cors.*` 설정 바인딩. 쿠키 인증과 호환되도록 `allowCredentials=true` 가 고정 정책이며
 * 그에 따라 `allowedOrigins` 에는 와일드카드 사용 불가 — 명시적 origin 목록.
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
