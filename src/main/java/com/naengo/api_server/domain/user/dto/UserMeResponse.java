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
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getProvider(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
