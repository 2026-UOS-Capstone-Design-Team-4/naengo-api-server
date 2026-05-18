package com.naengo.api_server.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.naengo.api_server.domain.recipe.entity.Ingredient;
import com.naengo.api_server.domain.recipe.entity.RecipeStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 관리자 제출 레시피 수정 — api-3.json {@code PendingRecipeAdminUpdate} 정합.
 *
 * <p>모든 필드 선택. 전달하지 않은(=null) 필드는 변경하지 않는다 (PATCH 시맨틱).
 * 요청 키는 api-3.json snake_case ({@code @JsonProperty}).
 */
public record PendingRecipeAdminUpdateRequest(
        @JsonProperty("title") @Size(max = 255) String title,
        @JsonProperty("content") @Size(max = 10000) String content,
        @JsonProperty("description") @Size(max = 1000) String description,
        @JsonProperty("ingredients") @Valid List<Ingredient> ingredients,
        @JsonProperty("ingredients_raw") @Size(max = 2000) String ingredientsRaw,
        @JsonProperty("instructions") List<@Size(max = 500) String> instructions,
        @JsonProperty("servings") BigDecimal servings,
        @JsonProperty("cooking_time") Integer cookingTime,
        @JsonProperty("calories") Integer calories,
        @JsonProperty("difficulty")
        @Pattern(regexp = "easy|normal|hard", message = "difficulty must be one of: easy, normal, hard")
        String difficulty,
        @JsonProperty("category") List<@Size(max = 50) String> category,
        @JsonProperty("tags") List<@Size(max = 50) String> tags,
        @JsonProperty("tips") List<@Size(max = 500) String> tips,
        @JsonProperty("video_url") @Size(max = 512) String videoUrl,
        @JsonProperty("image_url") @Size(max = 512) String imageUrl,
        @JsonProperty("status") RecipeStatus status,
        @JsonProperty("admin_note") @Size(max = 2000) String adminNote
) {
}
