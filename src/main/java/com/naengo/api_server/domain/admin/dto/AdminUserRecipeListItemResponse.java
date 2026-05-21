package com.naengo.api_server.domain.admin.dto;

import com.naengo.api_server.domain.recipe.entity.RecipeStatus;

import java.time.ZonedDateTime;

/**
 * 관리자용 user_recipes 목록 한 행 (요약). 단건 상세는 AdminUserRecipeDetailResponse.
 */
public record AdminUserRecipeListItemResponse(
        Integer userRecipeId,
        Integer userId,
        String userNickname,
        String title,
        String description,
        RecipeStatus status,
        String adminNote,
        ZonedDateTime reviewedAt,
        ZonedDateTime createdAt
) {
}
