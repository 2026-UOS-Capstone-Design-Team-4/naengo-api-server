package com.naengo.api_server.domain.admin.dto;

import java.util.List;

public record AdminUserRecipeListResponse(
        List<AdminUserRecipeListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
