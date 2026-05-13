package com.naengo.api_server.domain.recipe.dto;

import com.naengo.api_server.domain.recipe.entity.RecipeAuthorType;

import java.time.ZonedDateTime;

/**
 * 공개 레시피 목록(`recipes` 테이블 기반) 의 한 행. 페이로드 절약을 위해
 * 본문 / 재료 / 조리 순서 등은 단건 조회에서만 노출.
 */
public record RecipeListItemResponse(
        Long recipeId,
        String title,
        String description,
        String imageUrl,
        String authorNickname,
        RecipeAuthorType authorType,
        String difficulty,
        Integer cookingTime,
        int likesCount,
        int scrapCount,
        ZonedDateTime createdAt
) {
}
