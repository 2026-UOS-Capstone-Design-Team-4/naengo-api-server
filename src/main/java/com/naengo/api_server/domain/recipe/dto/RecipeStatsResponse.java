package com.naengo.api_server.domain.recipe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.naengo.api_server.domain.recipe.entity.RecipeStats;

/**
 * 좋아요/스크랩 변경 후 집계 응답 — api-3.json 의 {@code RecipeStatsResponse} 와 정합.
 */
public record RecipeStatsResponse(
        @JsonProperty("likes_count") int likesCount,
        @JsonProperty("scrap_count") int scrapCount
) {
    public static RecipeStatsResponse from(RecipeStats stats) {
        if (stats == null) return new RecipeStatsResponse(0, 0);
        return new RecipeStatsResponse(stats.getLikesCount(), stats.getScrapCount());
    }
}
