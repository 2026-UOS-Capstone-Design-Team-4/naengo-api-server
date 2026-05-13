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
 * 승인된 레시피 (관리자가 게시 결정한 것). 사용자 제출 레시피는 PendingRecipe 에 별도 존재.
 * AI 서버 OpenAPI 의 RecipeResponse 와 1:1 매핑.
 */
@Entity
@Table(name = "recipes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_id")
    private Long recipeId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Ingredient> ingredients;

    @Column(name = "ingredients_raw", columnDefinition = "TEXT", nullable = false)
    private String ingredientsRaw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> instructions;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal servings;

    @Column(name = "cooking_time", nullable = false)
    private Integer cookingTime;

    @Column
    private Integer calories;

    /** "easy" / "normal" / "hard" — V1 CHECK 제약 참조 */
    @Column(nullable = false, length = 10)
    private String difficulty;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> tags = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> tips = List.of();

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "video_url", length = 512)
    private String videoUrl;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 20)
    @Builder.Default
    private RecipeAuthorType authorType = RecipeAuthorType.ADMIN;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    // recipes.embedding 은 AI 서버가 관리. 엔티티에 매핑하지 않음 (validate 는 missing-from-entity 컬럼은 문제삼지 않음).

    @OneToOne(mappedBy = "recipe", fetch = FetchType.LAZY)
    private RecipeStats stats;

    public void deactivate() {
        this.isActive = false;
    }
}
