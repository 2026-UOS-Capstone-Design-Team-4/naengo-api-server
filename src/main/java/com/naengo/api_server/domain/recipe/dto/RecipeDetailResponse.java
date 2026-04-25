package com.naengo.api_server.domain.recipe.dto;

import com.naengo.api_server.domain.recipe.entity.Ingredient;
import com.naengo.api_server.domain.recipe.entity.RecipeSource;
import com.naengo.api_server.domain.recipe.entity.RecipeStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record RecipeDetailResponse(
        Long recipeId,
        String title,
        String fullContent,
        String imageUrl,
        RecipeSource source,
        RecipeStatus status,
        Long authorId,
        String authorNickname,
        List<Ingredient> ingredients,
        int likesCount,
        int scrapCount,
        ZonedDateTime createdAt
) {
}
