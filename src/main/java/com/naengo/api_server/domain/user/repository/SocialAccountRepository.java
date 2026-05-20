package com.naengo.api_server.domain.user.repository;

import com.naengo.api_server.domain.user.entity.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    /** 소셜 로그인 진입점 — 외부 (provider, provider_user_id) 로 link 된 우리 user 찾기. */
    Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

    /** 특정 user 의 모든 link (현 정책상 0 또는 1). 응답의 provider 표기에 사용. */
    List<SocialAccount> findByUserId(Long userId);

    /** 회원 탈퇴 시 link 제거 (anonymize 와 같은 트랜잭션). */
    @Modifying
    @Query("DELETE FROM SocialAccount s WHERE s.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
