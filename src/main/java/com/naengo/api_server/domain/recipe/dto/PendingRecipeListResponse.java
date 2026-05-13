package com.naengo.api_server.domain.recipe.dto;

import java.util.List;

public record PendingRecipeListResponse(
        List<PendingRecipeListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
