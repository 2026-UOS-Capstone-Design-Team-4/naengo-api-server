package com.naengo.api_server.domain.recipe.controller;

import com.naengo.api_server.domain.recipe.dto.RecipeListItemResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeListResponse;
import com.naengo.api_server.domain.recipe.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 공개 레시피 조회 — api-3.json `/api/v1/recipes`.
 *
 * <p>사용자 제출(pending) 흐름은 {@link UserRecipeController} 책임.
 */
@RestController
@RequestMapping("/api/v1/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    /** 공개 레시피 목록 — 커서 페이지네이션 (recipes, is_active=true). */
    @GetMapping
    public RecipeListResponse list(
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return recipeService.listApproved(sort, cursor, limit);
    }

    /** 단건 조회 (recipes 만). 응답은 목록 항목과 동형 (api-3.json 정합). */
    @GetMapping("/{id}")
    public RecipeListItemResponse detail(@PathVariable Integer id) {
        return recipeService.detail(id);
    }
}
