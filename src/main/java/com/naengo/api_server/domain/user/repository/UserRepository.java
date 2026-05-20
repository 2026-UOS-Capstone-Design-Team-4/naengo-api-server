package com.naengo.api_server.domain.user.repository;

import com.naengo.api_server.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    // 소셜 식별자(provider, provider_user_id) 조회는 V5 분리 이후
    // SocialAccountRepository.findByProviderAndProviderUserId 사용.
}
