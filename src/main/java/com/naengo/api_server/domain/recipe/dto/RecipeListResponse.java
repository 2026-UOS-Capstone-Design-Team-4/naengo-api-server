package com.naengo.api_server.domain.recipe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 커서 기반 레시피 목록 응답. api-3.json 의 {@code RecipeListResponse} 와 정합.
 *
 * <ul>
 *   <li>{@code next_cursor} — 다음 페이지 요청 시 그대로 전달. 마지막 페이지면 null.</li>
 *   <li>{@code has_next} — 다음 페이지 존재 여부.</li>
 * </ul>
 */
public record RecipeListResponse(
        @JsonProperty("items") List<RecipeListItemResponse> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_next") boolean hasNext
) {
}
