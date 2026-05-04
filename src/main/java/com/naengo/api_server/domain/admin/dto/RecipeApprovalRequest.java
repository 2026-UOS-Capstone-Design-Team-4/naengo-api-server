package com.naengo.api_server.domain.admin.dto;

import jakarta.validation.constraints.Size;

public record RecipeApprovalRequest(
        @Size(max = 1000) String adminNote
) {
}
