package com.naengo.api_server.domain.recipe.dto;

import com.naengo.api_server.domain.recipe.entity.RecipeStatus;

import java.time.ZonedDateTime;

/**
 * 사용자가 제출한 pending_recipes 목록의 한 행.
 */
public record PendingRecipeListItemResponse(
        Long pendingRecipeId,
        String title,
        String description,
        String imageUrl,
        RecipeStatus status,
        String adminNote,
        ZonedDateTime reviewedAt,
        ZonedDateTime createdAt
) {
}
