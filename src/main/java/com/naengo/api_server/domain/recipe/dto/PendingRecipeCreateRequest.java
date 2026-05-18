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
 * 사용자 레시피 제출 요청 — api-3.json {@code PendingRecipeCreate} 와 정합.
 * pending_recipes 테이블에 INSERT 된다.
 *
 * <p>최소 필수: title + content. 나머지는 선택 (관리자 승인 시 보정 가능).
 */
public record PendingRecipeCreateRequest(
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
