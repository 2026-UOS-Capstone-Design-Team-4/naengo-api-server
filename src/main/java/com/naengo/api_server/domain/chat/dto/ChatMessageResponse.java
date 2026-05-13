package com.naengo.api_server.domain.chat.dto;

import com.naengo.api_server.domain.recipe.dto.RecipeListItemResponse;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 채팅 메시지 한 건. role 은 "user" / "model" 소문자 (AI contract 정합).
 * recipes 가 null 이면 추천이 없었던 메시지 (사용자 입력 / 추천 없는 AI 응답).
 */
public record ChatMessageResponse(
        Long messageId,
        String role,
        String content,
        List<RecipeListItemResponse> recipes,
        ZonedDateTime createdAt
) {
}
