package com.naengo.api_server.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 8-1 CORS 검증.
 * - preflight (OPTIONS) 응답에 Access-Control-Allow-* 헤더 포함
 * - 실제 요청에 Access-Control-Allow-Origin + Allow-Credentials 포함
 * - 허용되지 않은 origin 은 거부 (403 또는 헤더 부재)
 * - Set-Cookie 가 exposed-headers 에 노출
 */
class CorsIntegrationTest extends IntegrationTestSupport {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final String DISALLOWED_ORIGIN = "https://evil.example";

    @Test
    @DisplayName("preflight OPTIONS — 허용 origin → 200 + Access-Control-Allow-* 헤더")
    void preflightAllowed() {
        ResponseEntity<Void> response = client.method(HttpMethod.OPTIONS)
                .uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, Authorization")
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders h = response.getHeaders();
        assertThat(h.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED_ORIGIN);
        assertThat(h.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(h.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("POST");
        // Max-Age (preflight 캐시) 도 노출
        assertThat(h.getFirst(HttpHeaders.ACCESS_CONTROL_MAX_AGE)).isEqualTo("3600");
    }

    @Test
    @DisplayName("preflight — 허용되지 않은 origin → CORS 헤더 부재 (브라우저가 차단)")
    void preflightDisallowedOrigin() {
        ResponseEntity<Void> response = client.method(HttpMethod.OPTIONS)
                .uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .retrieve().toBodilessEntity();

        // Spring Security 의 CORS 처리: 허용 안 된 origin 의 preflight 는 403 (또는 200 + 헤더 부재).
        // 둘 중 어느 쪽이든 브라우저에서 막힘. 핵심은 Access-Control-Allow-Origin 헤더 부재.
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isNotEqualTo(DISALLOWED_ORIGIN);
    }

    @Test
    @DisplayName("실제 GET 요청 — 허용 origin + 응답에 Allow-Origin / Allow-Credentials / 노출 Set-Cookie")
    void actualRequestAllowed() {
        ResponseEntity<String> response = client.get().uri("/health")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders h = response.getHeaders();
        assertThat(h.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED_ORIGIN);
        assertThat(h.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        // exposed-headers 에 Set-Cookie 가 포함되도록 설정 → 응답 헤더로 노출됨
        assertThat(h.getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .isNotNull()
                .contains("Set-Cookie");
    }

    @Test
    @DisplayName("Origin 없는 요청 — CORS 헤더 부재 (정상). 서버는 그냥 처리")
    void noOriginPassThrough() {
        ResponseEntity<String> response = client.get().uri("/health")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Same-origin 또는 비브라우저 호출 → CORS 검사 없이 통과
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isNull();
    }
}
