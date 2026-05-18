package com.naengo.api_server.domain.recipe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.naengo.api_server.domain.recipe.entity.Ingredient;
import com.naengo.api_server.domain.recipe.entity.PendingRecipe;
import com.naengo.api_server.domain.recipe.entity.RecipeStatus;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 사용자 제출 레시피 응답. api-3.json 의 {@code PendingRecipeResponse} 와 정합 (snake_case).
 *
 * <p>사용자 제출/조회 endpoint 공용. 관리자 endpoint 는 별도 DTO 유지 (PR-6 에서 통합 검토).
 */
public record PendingRecipeResponse(
        @JsonProperty("pending_recipe_id") Long pendingRecipeId,
        @JsonProperty("title") String title,
        @JsonProperty("content") String content,
        @JsonProperty("description") String description,
        @JsonProperty("ingredients") List<Ingredient> ingredients,
        @JsonProperty("ingredients_raw") String ingredientsRaw,
        @JsonProperty("instructions") List<String> instructions,
        @JsonProperty("servings") BigDecimal servings,
        @JsonProperty("cooking_time") Integer cookingTime,
        @JsonProperty("calories") Integer calories,
        @JsonProperty("difficulty") String difficulty,
        @JsonProperty("category") List<String> category,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("tips") List<String> tips,
        @JsonProperty("video_url") String videoUrl,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("status") RecipeStatus status,
        @JsonProperty("admin_note") String adminNote,
        @JsonProperty("reviewed_at") ZonedDateTime reviewedAt,
        @JsonProperty("created_at") ZonedDateTime createdAt
) {
    public static PendingRecipeResponse from(PendingRecipe p) {
        return new PendingRecipeResponse(
                p.getPendingRecipeId(),
                p.getTitle(),
                p.getContent(),
                p.getDescription(),
                p.getIngredients(),
                p.getIngredientsRaw(),
                p.getInstructions(),
                p.getServings(),
                p.getCookingTime(),
                p.getCalories(),
                p.getDifficulty(),
                p.getCategory(),
                p.getTags(),
                p.getTips(),
                p.getVideoUrl(),
                p.getImageUrl(),
                p.getStatus(),
                p.getAdminNote(),
                p.getReviewedAt(),
                p.getCreatedAt()
        );
    }
}
