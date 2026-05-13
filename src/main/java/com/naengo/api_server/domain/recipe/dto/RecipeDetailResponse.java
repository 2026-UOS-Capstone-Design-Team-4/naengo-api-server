package com.naengo.api_server.domain.recipe.dto;

import com.naengo.api_server.domain.recipe.entity.Ingredient;
import com.naengo.api_server.domain.recipe.entity.RecipeAuthorType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 단건 레시피 상세 응답. AI 서버 RecipeResponse 와 1:1 정합.
 */
public record RecipeDetailResponse(
        Long recipeId,
        String title,
        String description,
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
        String content,
        String videoUrl,
        String imageUrl,
        RecipeAuthorType authorType,
        Long authorId,
        String authorNickname,
        int likesCount,
        int scrapCount,
        ZonedDateTime createdAt
) {
}
