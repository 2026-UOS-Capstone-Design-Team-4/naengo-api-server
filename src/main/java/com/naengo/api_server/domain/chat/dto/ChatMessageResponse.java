package com.naengo.api_server.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.naengo.api_server.domain.recipe.dto.RecipeListItemResponse;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 채팅 메시지 한 건 — api-3.json {@code ChatMessageResponse} 정합 (snake_case).
 * role 은 "user" / "model" 소문자. recipes 가 null 이면 추천이 없었던 메시지.
 */
public record ChatMessageResponse(
        @JsonProperty("message_id") Long messageId,
        @JsonProperty("role") String role,
        @JsonProperty("content") String content,
        @JsonProperty("recipes") List<RecipeListItemResponse> recipes,
        @JsonProperty("created_at") ZonedDateTime createdAt
) {
}
