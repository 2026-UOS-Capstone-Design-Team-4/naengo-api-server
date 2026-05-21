package com.naengo.api_server.domain.recipe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.naengo.api_server.domain.recipe.entity.Ingredient;
import com.naengo.api_server.domain.recipe.entity.RecipeAuthorType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 레시피 단건/목록 공통 응답. api-3.json 의 {@code RecipeListItemResponse} 와 정합.
 *
 * <p>JSON 키는 api-3.json 에 맞춘 snake_case ({@code @JsonProperty}).
 * Java accessor 는 camelCase 유지 — {@code recipeId()} 는 ChatService 가 의존.
 *
 * <p>{@code authorNickname} 은 api-3.json 에 없는 우리 확장 (탈퇴 익명화 표시용).
 * 클라이언트는 모르는 필드를 무시하므로 호환에 문제 없음.
 */
public record RecipeListItemResponse(
        @JsonProperty("id") Integer recipeId,
        @JsonProperty("title") String title,
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
        @JsonProperty("author_type") RecipeAuthorType authorType,
        @JsonProperty("author_nickname") String authorNickname,
        @JsonProperty("created_at") ZonedDateTime createdAt,
        @JsonProperty("likes_count") int likesCount,
        @JsonProperty("scrap_count") int scrapCount,
        @JsonProperty("is_liked") boolean isLiked,
        @JsonProperty("is_scrapped") boolean isScrapped
) {
}
