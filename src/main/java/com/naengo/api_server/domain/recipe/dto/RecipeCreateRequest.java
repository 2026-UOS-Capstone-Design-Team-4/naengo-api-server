package com.naengo.api_server.domain.recipe.dto;

import com.naengo.api_server.domain.recipe.entity.Ingredient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 사용자 레시피 제출 요청. pending_recipes 테이블에 INSERT 된다.
 *
 * <p>최소 필수: title + content. 나머지는 선택 (관리자 승인 시 보정 가능).
 * AI 서버 contract (`docs/spec/ai-server-contract.md §3.1`) 의 RecipeResponse 스키마를
 * 따르되, 사용자 단계에서는 모든 메타가 강제는 아니다.
 */
public record RecipeCreateRequest(
        @NotBlank @Size(max = 255)   String title,
        @Size(max = 1000)            String description,
        @NotBlank @Size(max = 10000) String content,
        @Valid                       List<Ingredient> ingredients,
        @Size(max = 2000)            String ingredientsRaw,
        @Size(max = 50)              List<@NotBlank @Size(max = 500) String> instructions,
        @PositiveOrZero              BigDecimal servings,
        @Positive                    Integer cookingTime,
        @PositiveOrZero              Integer calories,
        @Pattern(regexp = "easy|normal|hard", message = "difficulty must be one of: easy, normal, hard")
                                     String difficulty,
        @Size(max = 20)              List<@NotBlank @Size(max = 50) String> category,
        @Size(max = 20)              List<@NotBlank @Size(max = 50) String> tags,
        @Size(max = 20)              List<@NotBlank @Size(max = 500) String> tips,
        @Size(max = 512)             String videoUrl,
        @Size(max = 512)             String imageUrl
) {
}
