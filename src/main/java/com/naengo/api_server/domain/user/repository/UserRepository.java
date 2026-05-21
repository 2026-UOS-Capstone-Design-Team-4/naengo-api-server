package com.naengo.api_server.domain.user.repository;

import com.naengo.api_server.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByNickname(String nickname);

    // 소셜 식별자(provider, provider_user_id) 조회는 user_identities 테이블로 분리됨
    // → UserIdentityRepository (Phase 3 에서 신설).
}
