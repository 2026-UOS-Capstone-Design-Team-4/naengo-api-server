package com.naengo.api_server.domain.recipe.entity;

/**
 * AI 서버 OpenAPI 의 author_type 과 정합. recipes / pending_recipes 양쪽에서 사용.
 */
public enum RecipeAuthorType {
    ADMIN,
    USER
}
