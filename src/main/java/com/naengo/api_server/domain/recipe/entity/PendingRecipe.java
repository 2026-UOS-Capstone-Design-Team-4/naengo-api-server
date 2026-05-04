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
}
