package com.naengo.api_server.domain.admin.dto;

import com.naengo.api_server.domain.recipe.entity.Ingredient;
import com.naengo.api_server.domain.recipe.entity.RecipeStatus;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 관리자용 pending_recipes 단건 상세 — 모든 컬럼 노출.
 */
public record AdminPendingRecipeDetailResponse(
        Long pendingRecipeId,
        Long userId,
        String userNickname,
        String title,
        String description,
        String content,
        List<Ingredient> ingredients,
        String ingredientsRaw,
        List<String> instructions,
        BigDecimal servings,
        Integer cookingTime,
        Integer calories,
        String difficulty,
        List<String> category,
        List<String> tags,
        List<String> tips,
        String videoUrl,
        String imageUrl,
        RecipeStatus status,
        String adminNote,
        ZonedDateTime reviewedAt,
        ZonedDateTime createdAt
) {
}
