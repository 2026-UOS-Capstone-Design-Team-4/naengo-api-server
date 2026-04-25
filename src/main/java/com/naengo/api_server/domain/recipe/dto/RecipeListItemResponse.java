package com.naengo.api_server.domain.recipe.dto;

import com.naengo.api_server.domain.recipe.entity.RecipeStatus;

import java.time.ZonedDateTime;

public record RecipeListItemResponse(
        Long recipeId,
        String title,
        String imageUrl,
        String authorNickname,
        RecipeStatus status,
        int likesCount,
        int scrapCount,
        ZonedDateTime createdAt
) {
}
