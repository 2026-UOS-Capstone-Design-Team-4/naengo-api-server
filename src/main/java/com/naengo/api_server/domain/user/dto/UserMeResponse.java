package com.naengo.api_server.domain.user.dto;

import com.naengo.api_server.domain.user.entity.AuthProvider;
import com.naengo.api_server.domain.user.entity.User;

import java.time.ZonedDateTime;

/**
 * 마이페이지 응답 (옵션 A — DBv5 정합 후).
 * email → username, userId Long → Integer.
 */
public record UserMeResponse(
        Integer userId,
        String username,
        String nickname,
        String role,
        AuthProvider provider,
        boolean isActive,
        ZonedDateTime createdAt
) {
    /**
     * provider 는 user_identities 조회로 결정되므로 호출부(UserMeService) 가 주입.
     */
    public static UserMeResponse from(User user, AuthProvider provider) {
        return new UserMeResponse(
                user.getUserId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole(),
                provider,
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
