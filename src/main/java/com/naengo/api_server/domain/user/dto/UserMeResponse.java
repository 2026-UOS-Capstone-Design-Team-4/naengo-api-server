package com.naengo.api_server.domain.user.dto;

import com.naengo.api_server.domain.user.entity.AuthProvider;
import com.naengo.api_server.domain.user.entity.User;

import java.time.ZonedDateTime;

public record UserMeResponse(
        Long userId,
        String email,
        String nickname,
        String role,
        AuthProvider provider,
        boolean isActive,
        ZonedDateTime createdAt
) {
    /**
     * V5 분리 이후 — provider 는 더 이상 users 컬럼이 아니므로 외부에서 계산해 주입.
     * 호출부(UserMeService) 가 social_accounts 조회 결과로 결정한다.
     */
    public static UserMeResponse from(User user, AuthProvider provider) {
        return new UserMeResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                provider,
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
