package com.naengo.api_server.domain.recipe.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI 서버 OpenAPI 의 IngredientItem 과 정합되는 재료 한 줄.
 * (`docs/spec/ai-server-contract.md §3.3`)
 */
public record Ingredient(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50)  String amount,
        @NotBlank @Size(max = 20)  String unit,
        @NotBlank @Size(max = 20)  String type,
        @Size(max = 200)           String note
) {
}
