package com.naengo.api_server.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-20260507-01 (auth-cookie) 검증.
 * - 발급: signup/login 응답에 Set-Cookie + body accessToken 동시
 * - 검증: 헤더 / 쿠키 / 둘 다 / 둘 다 없음 분기
 * - 만료: logout / withdraw 시 Max-Age=0 쿠키
 */
class AuthCookieIntegrationTest extends IntegrationTestSupport {

    private static final String SIGNUP = """
            {"username":"a@b.c","password":"pw12345A","nickname":"alice"}
            """;
    private static final String LOGIN = """
            {"username":"a@b.c","password":"pw12345A"}
            """;

    @Test
    @DisplayName("signup → 201 + Set-Cookie(NAENGO_AT) + body.accessToken 동시")
    void signupReturnsBothCookieAndToken() {
        ResponseEntity<String> response = postJson("/api/v1/auth/signup", SIGNUP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = Objects.requireNonNull(response.getBody());
        String token = extractField(body, "access_token");
        assertThat(token).isNotBlank();

        // §5-1 — 가입 즉시 user_profiles row 자동 생성
        long userId = Long.parseLong(extractField(body, "user_id"));
        Number profileCount = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM user_profiles WHERE user_id = :id")
                .setParameter("id", userId).getSingleResult();
        assertThat(profileCount.longValue()).isEqualTo(1L);

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().anySatisfy(c -> {
            assertThat(c).startsWith("NAENGO_AT=" + token);
            assertThat(c).contains("HttpOnly");
            assertThat(c).contains("Path=/");
            assertThat(c).contains("Max-Age=86400");
            assertThat(c).contains("SameSite=Lax");
        });
    }

    @Test
    @DisplayName("login → 200 + Set-Cookie + body.accessToken 동시")
    void loginReturnsBothCookieAndToken() {
        postJson("/api/v1/auth/signup", SIGNUP);

        ResponseEntity<String> response = postJson("/api/v1/auth/login", LOGIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extractField(response.getBody(), "access_token")).isNotBlank();
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNotEmpty();
    }

    @Test
    @DisplayName("Authorization 헤더만 사용 → /api/users/me 200")
    void protectedEndpointWithHeaderOnly() {
        String token = extractField(postJson("/api/v1/auth/signup", SIGNUP).getBody(), "access_token");

        ResponseEntity<String> response = client.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"username\":\"a@b.c\"");
    }

    @Test
    @DisplayName("쿠키만 사용 → /api/users/me 200")
    void protectedEndpointWithCookieOnly() {
        String cookieHeader = extractCookieHeader(postJson("/api/v1/auth/signup", SIGNUP));

        ResponseEntity<String> response = client.get().uri("/api/v1/users/me")
                .header(HttpHeaders.COOKIE, cookieHeader)
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"username\":\"a@b.c\"");
    }

    @Test
    @DisplayName("헤더 + 쿠키 동시 → 헤더 우선 (200, 쿠키는 일부러 깨도 OK)")
    void protectedEndpointWithBoth() {
        String token = extractField(postJson("/api/v1/auth/signup", SIGNUP).getBody(), "access_token");

        ResponseEntity<String> response = client.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.COOKIE, "NAENGO_AT=invalid-cookie-value")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("토큰 / 쿠키 둘 다 없음 → 401 + ErrorResponse(UNAUTHENTICATED)")
    void protectedEndpointUnauthorized() {
        ResponseEntity<String> response = client.get().uri("/api/v1/users/me")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody())
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("로그인이 필요합니다");
    }

    @Test
    @DisplayName("logout → 204 + Set-Cookie Max-Age=0 (인증 없이도 멱등)")
    void logoutExpiresCookie() {
        ResponseEntity<Void> response = client.post().uri("/api/v1/auth/logout")
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anySatisfy(c -> {
            assertThat(c).startsWith("NAENGO_AT=;");
            assertThat(c).contains("Max-Age=0");
        });
    }

    @Test
    @DisplayName("withdraw → 204 + Set-Cookie Max-Age=0 + 익명화. 이후 토큰 재사용 시 401")
    void withdrawExpiresCookieAndBlocksToken() {
        String token = extractField(postJson("/api/v1/auth/signup", SIGNUP).getBody(), "access_token");

        ResponseEntity<Void> withdraw = client.delete().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toBodilessEntity();

        assertThat(withdraw.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(withdraw.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(c -> c.contains("NAENGO_AT=;") && c.contains("Max-Age=0"));

        // 같은 토큰 재사용 → 401 (is_blocked + EntryPoint 협력)
        ResponseEntity<String> reuse = client.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── 헬퍼 ───────────────────────────────────────────────

    private ResponseEntity<String> postJson(String url, String body) {
        return client.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(String.class);
    }

    /** Set-Cookie 헤더에서 NAENGO_AT=<value> 첫 segment 만 추출 (Cookie 헤더 형식). */
    private String extractCookieHeader(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        for (String c : setCookies) {
            if (c.startsWith("NAENGO_AT=")) {
                int semi = c.indexOf(';');
                return semi < 0 ? c : c.substring(0, semi);
            }
        }
        throw new AssertionError("NAENGO_AT cookie not found in Set-Cookie");
    }

    /** 단순 JSON 파싱 — "key":"value" 또는 "key":bare. */
    static String extractField(String json, String key) {
        String marker = "\"" + key + "\":";
        int idx = json.indexOf(marker);
        if (idx < 0) throw new AssertionError("key not found: " + key);
        int start = idx + marker.length();
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return json.substring(start + 1, end);
        }
        int end = start;
        while (end < json.length() && ",}".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(start, end).trim();
    }
}
