package com.naengo.api_server.domain.recipe.dto;

import com.naengo.api_server.domain.recipe.entity.Ingredient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RecipeCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 10000) String fullContent,
        @Size(max = 512) String imageUrl,
        @NotEmpty @Valid List<Ingredient> ingredients
) {
}
