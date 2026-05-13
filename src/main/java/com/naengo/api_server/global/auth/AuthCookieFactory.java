package com.naengo.api_server.global.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * JWT 를 HttpOnly Cookie 로 발급/만료시키는 팩토리. 설정은 `auth.cookie.*` (application.yml).
 *
 * <p>모바일 호환을 위해 클라이언트는 응답 body 의 accessToken 을 그대로 받을 수도 있고,
 * 브라우저는 쿠키를 자동으로 붙여 보낼 수도 있다. 서버는 헤더(`Authorization: Bearer`) 우선,
 * 없으면 쿠키 fallback (`JwtAuthenticationFilter`).
 */
@Component
public class AuthCookieFactory {

    @Value("${auth.cookie.name}")
    private String name;

    @Value("${auth.cookie.max-age-seconds}")
    private long maxAgeSeconds;

    @Value("${auth.cookie.path}")
    private String path;

    @Value("${auth.cookie.secure}")
    private boolean secure;

    @Value("${auth.cookie.same-site}")
    private String sameSite;

    @Value("${auth.cookie.domain:}")
    private String domain;

    /** 로그인/가입 성공 시 사용 — JWT 가 담긴 쿠키. */
    public ResponseCookie build(String token) {
        return baseBuilder(token, maxAgeSeconds).build();
    }

    /** 로그아웃/탈퇴 시 사용 — 즉시 만료 (Max-Age=0, value="") */
    public ResponseCookie expire() {
        return baseBuilder("", 0).build();
    }

    public String getName() {
        return name;
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value, long maxAge) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .sameSite(sameSite)
                .maxAge(maxAge);
        if (domain != null && !domain.isBlank()) {
            b.domain(domain);
        }
        return b;
    }
}
