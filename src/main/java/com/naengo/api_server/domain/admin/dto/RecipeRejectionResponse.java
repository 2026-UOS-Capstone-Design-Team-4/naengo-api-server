package com.naengo.api_server.domain.admin.dto;

import com.naengo.api_server.domain.recipe.entity.RecipeStatus;

import java.time.ZonedDateTime;

public record RecipeRejectionResponse(
        Long pendingRecipeId,
        RecipeStatus status,
        String adminNote,
        ZonedDateTime reviewedAt
) {
}
