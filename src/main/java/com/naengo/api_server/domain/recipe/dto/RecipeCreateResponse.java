package com.naengo.api_server.domain.recipe.dto;

import com.naengo.api_server.domain.recipe.entity.RecipeStatus;

/**
 * 사용자 레시피 제출 응답. id 는 pending_recipe_id (실제 recipes 의 PK 가 아님 — 승인 후 별도 발급).
 */
public record RecipeCreateResponse(Long pendingRecipeId, RecipeStatus status) {
}
