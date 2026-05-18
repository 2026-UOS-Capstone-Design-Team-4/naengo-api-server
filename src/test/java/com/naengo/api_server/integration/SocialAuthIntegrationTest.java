package com.naengo.api_server.integration;

import com.naengo.api_server.global.auth.oauth.KakaoOAuthClient;
import com.naengo.api_server.global.auth.oauth.OAuthUserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 카카오 소셜 로그인 통합 — `KakaoOAuthClient` 를 mock 으로 대체해
 * 신규 가입 / 재로그인 / 차단 / 이메일 충돌 / placeholder 이메일 케이스 검증.
 */
class SocialAuthIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    KakaoOAuthClient kakaoOAuthClient;

    @Test
    @DisplayName("신규 카카오 사용자 → 200 + 토큰/쿠키 + user_profiles row 자동 생성")
    void newKakaoUserCreatesProfile() {
        when(kakaoOAuthClient.getUserInfo(anyString()))
                .thenReturn(new OAuthUserInfo("kakao-1001", "k1@kakao.com"));

        ResponseEntity<String> res = kakaoLogin("tok");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = AuthCookieIntegrationTest.extractField(res.getBody(), "access_token");
        assertThat(token).isNotBlank();
        assertThat(res.getHeaders().get(HttpHeaders.SET_COOKIE)).isNotEmpty();

        long userId = Long.parseLong(AuthCookieIntegrationTest.extractField(res.getBody(), "user_id"));
        assertThat(profileCount(userId)).isEqualTo(1);

        // 토큰으로 마이페이지 프로필 접근 → 200 + 빈 user_input
        ResponseEntity<String> profile = client.get().uri("/api/v1/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody()).contains("\"user_input\":[]");
    }

    @Test
    @DisplayName("기존 카카오 사용자 재로그인 → 동일 user_id, 중복 생성 없음")
    void existingKakaoUserRelogin() {
        when(kakaoOAuthClient.getUserInfo(anyString()))
                .thenReturn(new OAuthUserInfo("kakao-2002", "k2@kakao.com"));

        long firstId = Long.parseLong(
                AuthCookieIntegrationTest.extractField(kakaoLogin("t1").getBody(), "user_id"));
        long secondId = Long.parseLong(
                AuthCookieIntegrationTest.extractField(kakaoLogin("t2").getBody(), "user_id"));

        assertThat(secondId).isEqualTo(firstId);
        assertThat(userCountByProviderId("kakao-2002")).isEqualTo(1);
        assertThat(profileCount(firstId)).isEqualTo(1);
    }

    @Test
    @DisplayName("차단된 카카오 사용자 → 403 USER_BLOCKED")
    void blockedKakaoUserRejected() {
        when(kakaoOAuthClient.getUserInfo(anyString()))
                .thenReturn(new OAuthUserInfo("kakao-3003", "k3@kakao.com"));
        long userId = Long.parseLong(
                AuthCookieIntegrationTest.extractField(kakaoLogin("t").getBody(), "user_id"));

        transactionTemplate.executeWithoutResult(s ->
                entityManager.createNativeQuery("UPDATE users SET is_blocked = true WHERE user_id = :id")
                        .setParameter("id", userId).executeUpdate());

        ResponseEntity<String> blocked = kakaoLogin("t-again");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blocked.getBody()).contains("\"code\":\"USER_BLOCKED\"");
    }

    @Test
    @DisplayName("동일 이메일이 LOCAL 로 이미 가입 → 409 EMAIL_PROVIDER_CONFLICT")
    void emailConflictWithLocalAccount() {
        // 자체 회원가입 (LOCAL)
        client.post().uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"email\":\"dup@kakao.com\",\"password\":\"pw12345A\",\"nickname\":\"dupuser\"}")
                .retrieve().toBodilessEntity();

        // 같은 이메일로 카카오 로그인 시도
        when(kakaoOAuthClient.getUserInfo(anyString()))
                .thenReturn(new OAuthUserInfo("kakao-4004", "dup@kakao.com"));

        ResponseEntity<String> conflict = kakaoLogin("t");
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("\"code\":\"EMAIL_PROVIDER_CONFLICT\"");
    }

    @Test
    @DisplayName("카카오 이메일 미동의 placeholder → 정상 가입 + 프로필 생성")
    void placeholderEmailSignsUp() {
        // 카카오가 이메일 미제공 시 KakaoOAuthClient 가 만드는 placeholder 형태
        when(kakaoOAuthClient.getUserInfo(anyString()))
                .thenReturn(new OAuthUserInfo("kakao-5005", "kakao_5005@social.naengo.com"));

        ResponseEntity<String> res = kakaoLogin("t");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        long userId = Long.parseLong(AuthCookieIntegrationTest.extractField(res.getBody(), "user_id"));
        assertThat(profileCount(userId)).isEqualTo(1);
    }

    // ─── 헬퍼 ───────────────────────────────────────────────

    private ResponseEntity<String> kakaoLogin(String accessToken) {
        return client.post().uri("/api/v1/auth/social/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"access_token\":\"" + accessToken + "\"}")
                .retrieve().toEntity(String.class);
    }

    private long profileCount(long userId) {
        Number n = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM user_profiles WHERE user_id = :id")
                .setParameter("id", userId).getSingleResult();
        return n.longValue();
    }

    private long userCountByProviderId(String providerId) {
        Number n = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM users WHERE provider_id = :pid")
                .setParameter("pid", providerId).getSingleResult();
        return n.longValue();
    }
}
