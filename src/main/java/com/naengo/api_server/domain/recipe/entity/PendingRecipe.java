package com.naengo.api_server.domain.recipe.entity;

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
 * 사용자 제출 레시피 (관리자 승인 → recipes 로 이동).
 * 입력 단계에서는 자유 형식 content 만 필수, 나머지는 선택.
 */
@Entity
@Table(name = "pending_recipes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PendingRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pending_recipe_id")
    private Long pendingRecipeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Ingredient> ingredients;

    @Column(name = "ingredients_raw", columnDefinition = "TEXT")
    private String ingredientsRaw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> instructions;

    @Column(precision = 4, scale = 1)
    private BigDecimal servings;

    @Column(name = "cooking_time")
    private Integer cookingTime;

    @Column
    private Integer calories;

    /** "easy" / "normal" / "hard" 또는 null */
    @Column(length = 10)
    private String difficulty;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tips;

    @Column(name = "video_url", length = 512)
    private String videoUrl;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RecipeStatus status = RecipeStatus.PENDING;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "reviewed_at")
    private ZonedDateTime reviewedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public void cancel() {
        this.isActive = false;
    }

    public void markApproved(String adminNote) {
        this.status = RecipeStatus.APPROVED;
        this.adminNote = adminNote;
        this.reviewedAt = ZonedDateTime.now();
    }

    public void markRejected(String reason) {
        this.status = RecipeStatus.REJECTED;
        this.adminNote = reason;
        this.reviewedAt = ZonedDateTime.now();
    }

    /**
     * 관리자 부분 수정 — null 인 인자는 기존 값 보존 (PATCH 시맨틱).
     * 상태/검토시각은 별도 ({@link #markApproved}/{@link #markRejected}/{@link #touchReviewed}).
     */
    public void applyAdminPatch(String title, String content, String description,
                                List<Ingredient> ingredients, String ingredientsRaw,
                                List<String> instructions, BigDecimal servings,
                                Integer cookingTime, Integer calories, String difficulty,
                                List<String> category, List<String> tags, List<String> tips,
                                String videoUrl, String imageUrl) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        if (description != null) this.description = description;
        if (ingredients != null) this.ingredients = ingredients;
        if (ingredientsRaw != null) this.ingredientsRaw = ingredientsRaw;
        if (instructions != null) this.instructions = instructions;
        if (servings != null) this.servings = servings;
        if (cookingTime != null) this.cookingTime = cookingTime;
        if (calories != null) this.calories = calories;
        if (difficulty != null) this.difficulty = difficulty;
        if (category != null) this.category = category;
        if (tags != null) this.tags = tags;
        if (tips != null) this.tips = tips;
        if (videoUrl != null) this.videoUrl = videoUrl;
        if (imageUrl != null) this.imageUrl = imageUrl;
    }

    /** status 전이 없이 admin_note 만 갱신. */
    public void setAdminNote(String adminNote) {
        if (adminNote != null) this.adminNote = adminNote;
    }

    /** status 변경 시 검토 시각 기록 (api-3.json: status 변경 → reviewed_at = NOW). */
    public void changeStatus(RecipeStatus newStatus) {
        this.status = newStatus;
        this.reviewedAt = ZonedDateTime.now();
    }
}
