package com.naengo.api_server.domain.recipe.dto;

import com.naengo.api_server.domain.recipe.entity.RecipeStatus;

public record RecipeCreateResponse(Long recipeId, RecipeStatus status) {
}
