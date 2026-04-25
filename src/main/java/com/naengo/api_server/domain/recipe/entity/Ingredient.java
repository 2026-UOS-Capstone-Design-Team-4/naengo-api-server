package com.naengo.api_server.domain.recipe.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Ingredient(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String amount
) {
}
