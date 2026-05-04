package com.naengo.api_server.domain.admin.dto;

import java.util.List;

public record AdminPendingRecipeListResponse(
        List<AdminPendingRecipeListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
