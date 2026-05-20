package com.naengo.api_server.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 사용자(`users`) ↔ 외부 소셜 제공자 계정 link (V5).
 *
 * <p>한 {@code user_id} 는 provider 별 1개 row 까지 link 가능 (UNIQUE(user_id, provider)).
 * 한 외부 계정(provider + providerUserId)은 우리 사용자 1명에게만 link (UNIQUE(provider, provider_user_id)).
 * LOCAL 가입자는 row 없음. 탈퇴(users 행 삭제 시) FK CASCADE 로 함께 제거.
 */
@Entity
@Table(name = "social_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_account_id")
    private Long socialAccountId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** "KAKAO" — V5 CHECK 제약 참조. 신규 provider 추가 시 enum {@link AuthProvider} + CHECK 동시 확장. */
    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "linked_at", updatable = false)
    @Builder.Default
    private ZonedDateTime linkedAt = ZonedDateTime.now();
}
