package com.naengo.api_server.domain.user.entity;

/**
 * 인증 출처 표기.
 *
 * <p>{@code LOCAL} 은 응답 표기 전용 — DBv5 의 {@code user_identities.provider} CHECK 는
 * {@code KAKAO/GOOGLE/NAVER/APPLE} 만 허용하며, LOCAL 가입자는 {@code user_identities}
 * 에 row 가 없다 ({@code password_hash != null} 로 판정).
 *
 * <p>현 단계 service 는 KAKAO 만 구현. GOOGLE/NAVER/APPLE 은 enum 등록만 — DBv5 CHECK
 * 호환 + 추후 신규 OAuthClient 추가 시 즉시 사용 가능.
 */
public enum AuthProvider {
    LOCAL,
    KAKAO,
    GOOGLE,
    NAVER,
    APPLE
}
