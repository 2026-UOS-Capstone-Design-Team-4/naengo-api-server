package com.naengo.api_server.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private Integer userId;
    private String nickname;
    private String role;
    private String accessToken;
}
