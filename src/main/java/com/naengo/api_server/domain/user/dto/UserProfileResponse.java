package com.naengo.api_server.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 사용자 직접 입력 취향/알레르기 — api-3.json {@code UserProfileResponse} 정합.
 * {@code user_input} 배열만 노출 (AI 분석 필드는 별도 `/preferences` 확장 endpoint).
 */
public record UserProfileResponse(
        @JsonProperty("user_input") List<String> userInput
) {
    public static UserProfileResponse of(List<String> userInput) {
        return new UserProfileResponse(userInput == null ? List.of() : userInput);
    }
}
