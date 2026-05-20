package com.naengo.api_server.domain.recipe.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * pending_recipes.draft_payload / ai_suggested_patch 의 JSONB 구조.
 *
 * <p>키는 naengo-ai schema.sql 의 draft_payload 기본값과 1:1 (snake_case 고정 —
 * {@code @JsonProperty}). AI 가 같은 JSONB 를 읽으므로 키 이름을 강제한다.
 * 평면 응답(api-3.json)의 단일 {@code ingredients_raw} 문자열은 매핑 경계에서
 * 이 배열과 join/split 한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecipeDraft {

    @JsonProperty("description")
    private String description;

    @JsonProperty("ingredients")
    @Builder.Default
    private List<Ingredient> ingredients = new ArrayList<>();

    @JsonProperty("ingredients_raw")
    @Builder.Default
    private List<String> ingredientsRaw = new ArrayList<>();

    @JsonProperty("instructions")
    @Builder.Default
    private List<String> instructions = new ArrayList<>();

    @JsonProperty("servings")
    private BigDecimal servings;

    @JsonProperty("cooking_time_minutes")
    private Integer cookingTimeMinutes;

    @JsonProperty("kcal_per_serving")
    private Integer kcalPerServing;

    @JsonProperty("difficulty")
    private String difficulty;

    @JsonProperty("category")
    @Builder.Default
    private List<String> category = new ArrayList<>();

    @JsonProperty("tags")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @JsonProperty("tips")
    @Builder.Default
    private List<String> tips = new ArrayList<>();

    @JsonProperty("video_url")
    private String videoUrl;

    @JsonProperty("image_url")
    private String imageUrl;

    public static RecipeDraft empty() {
        return RecipeDraft.builder().build();
    }
}
