package com.naengo.api_server.domain.user.dto;

import com.naengo.api_server.domain.user.entity.UserProfile;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 본인 선호도 조회 / 갱신 응답. 직접 입력 + AI 분석 모두 포함 (AI 분석은 read-only).
 * JSONB 배열 컬럼이 null 이면 빈 배열로 정규화하여 클라이언트 처리 단순화.
 */
public record UserPreferencesResponse(
        // 직접 입력
        List<String> userInput,
        String cookingSkill,
        Integer preferredCookingTime,
        BigDecimal servingSize,

        // AI 분석 (read-only)
        List<String> allergies,
        List<String> dietaryRestrictions,
        List<String> preferredIngredients,
        List<String> dislikedIngredients,
        List<String> preferredCategories,
        List<String> frequentlyUsedIngredients,
        List<String> tasteKeywords,
        List<Long> recentRecipeIds,

        ZonedDateTime aiAnalyzedAt,
        ZonedDateTime updatedAt
) {
    public static UserPreferencesResponse from(UserProfile p) {
        return new UserPreferencesResponse(
                nz(p.getUserInput()),
                p.getCookingSkill(),
                p.getPreferredCookingTime(),
                p.getServingSize(),
                nz(p.getAllergies()),
                nz(p.getDietaryRestrictions()),
                nz(p.getPreferredIngredients()),
                nz(p.getDislikedIngredients()),
                nz(p.getPreferredCategories()),
                nz(p.getFrequentlyUsedIngredients()),
                nz(p.getTasteKeywords()),
                nz(p.getRecentRecipeIds()),
                p.getAiAnalyzedAt(),
                p.getUpdatedAt()
        );
    }

    private static <T> List<T> nz(List<T> list) {
        return list == null ? List.of() : list;
    }
}
