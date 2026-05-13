package com.naengo.api_server.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 8-3 RequestIdFilter 검증.
 * - 클라이언트가 X-Request-Id 보내면 그대로 응답
 * - 안 보내면 서버가 UUID 생성하여 응답
 */
class RequestIdIntegrationTest extends IntegrationTestSupport {

    @Test
    @DisplayName("X-Request-Id 헤더 미포함 → 서버가 UUID 생성하여 응답")
    void serverGeneratesUuidWhenAbsent() {
        ResponseEntity<String> response = client.get().uri("/health")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String requestId = response.getHeaders().getFirst("X-Request-Id");
        assertThat(requestId)
                .isNotNull()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("X-Request-Id 헤더 포함 → 같은 값으로 응답 (분산 추적 호환)")
    void serverEchoesProvidedId() {
        String provided = "test-trace-12345";
        ResponseEntity<String> response = client.get().uri("/health")
                .header("X-Request-Id", provided)
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo(provided);
    }

    @Test
    @DisplayName("연속된 두 요청은 서로 다른 request-id (UUID 고유성)")
    void differentRequestsGetDifferentIds() {
        ResponseEntity<String> r1 = client.get().uri("/health").retrieve().toEntity(String.class);
        ResponseEntity<String> r2 = client.get().uri("/health").retrieve().toEntity(String.class);

        String id1 = r1.getHeaders().getFirst("X-Request-Id");
        String id2 = r2.getHeaders().getFirst("X-Request-Id");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("CORS 헤더와 동시 노출 (RequestIdFilter 가 SecurityFilterChain 보다 먼저 실행)")
    void requestIdSetEvenWithCors() {
        ResponseEntity<String> response = client.get().uri("/health")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotNull();
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:3000");
    }
}
