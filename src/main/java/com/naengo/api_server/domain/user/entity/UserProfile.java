package com.naengo.api_server.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 사용자 선호도. {@code users} 와 1:1 (`user_id` 가 PK 이자 FK).
 *
 * <p>두 종류 데이터:
 * <ul>
 *   <li>사용자 직접 입력 — {@code userInput}, {@code cookingSkill},
 *       {@code preferredCookingTime}, {@code servingSize}.</li>
 *   <li>AI 분석 결과 — 알레르기·식이제한·선호/기피 재료 등. AI 서버가 채움.</li>
 * </ul>
 *
 * 본 엔티티의 setter / 변경 메서드는 직접 입력 영역 한정 (AI 분석 필드는 보호).
 */
@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_input", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> userInput = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> allergies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dietary_restrictions", columnDefinition = "jsonb")
    private List<String> dietaryRestrictions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_ingredients", columnDefinition = "jsonb")
    private List<String> preferredIngredients;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "disliked_ingredients", columnDefinition = "jsonb")
    private List<String> dislikedIngredients;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_categories", columnDefinition = "jsonb")
    private List<String> preferredCategories;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "frequently_used_ingredients", columnDefinition = "jsonb")
    private List<String> frequentlyUsedIngredients;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "taste_keywords", columnDefinition = "jsonb")
    private List<String> tasteKeywords;

    /** "easy" / "normal" / "hard" 또는 null */
    @Column(name = "cooking_skill", length = 10)
    private String cookingSkill;

    @Column(name = "preferred_cooking_time")
    private Integer preferredCookingTime;

    @Column(name = "serving_size", precision = 4, scale = 1)
    private BigDecimal servingSize;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recent_recipe_ids", columnDefinition = "jsonb")
    private List<Long> recentRecipeIds;

    @Column(name = "ai_analyzed_at")
    private ZonedDateTime aiAnalyzedAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    /** 빈 default — 신규 사용자가 처음 PUT 할 때 사용. updated_at NOT NULL 충족. */
    public static UserProfile empty(Long userId) {
        return UserProfile.builder()
                .userId(userId)
                .userInput(List.of())
                .updatedAt(ZonedDateTime.now())
                .build();
    }

    /**
     * 직접 입력 영역만 부분 갱신. null 인 인자는 기존 값 보존.
     * AI 분석 영역은 손대지 않는다.
     */
    public void updateUserEditable(
            List<String> userInput,
            String cookingSkill,
            Integer preferredCookingTime,
            BigDecimal servingSize) {
        if (userInput != null) this.userInput = userInput;
        if (cookingSkill != null) this.cookingSkill = cookingSkill;
        if (preferredCookingTime != null) this.preferredCookingTime = preferredCookingTime;
        if (servingSize != null) this.servingSize = servingSize;
        this.updatedAt = ZonedDateTime.now();
    }
}
