package com.naengo.api_server.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecipeRejectionRequest(
        @NotBlank(message = "반려 사유는 필수 입력입니다.")
        @Size(min = 1, max = 1000, message = "반려 사유는 1~1000자 이내여야 합니다.")
        String reason
) {
}
