package com.naengo.api_server.domain.recipe.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 승인된 레시피 (정규화 코어). 재료/조리/라벨/미디어는 분리 테이블로 정규화되며
 * 클라이언트 응답(api-3.json RecipeResponse) 은 매퍼가 평면으로 조립한다.
 *
 * <p>recipe_stats / recipe_classifications / recipe_quality_scores 1:1 행은
 * DB 트리거(trigger_create_recipe_dependents)가 INSERT 시 자동 생성하므로
 * 애플리케이션에서 cascade 하지 않는다.
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

    @Column(name = "source_id")
    private Long sourceId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal servings;

    @Column(name = "cooking_time_minutes", nullable = false)
    private Integer cookingTimeMinutes;

    @Column(name = "kcal_per_serving")
    private Integer kcalPerServing;

    /** "easy" / "normal" / "hard" — V4 CHECK 제약 참조 */
    @Column(nullable = false, length = 10)
    private String difficulty;

    /** "PUBLIC" / "ADMIN_ONLY" */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String visibility = "PUBLIC";

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 20)
    @Builder.Default
    private RecipeAuthorType authorType = RecipeAuthorType.ADMIN;

    @Column(name = "author_id")
    private Long authorId;

    /** "NOT_CLASSIFIED" / "CLASSIFIED" / "FAILED" / "REVIEW_REQUIRED" */
    @Column(name = "classification_status", nullable = false, length = 30)
    @Builder.Default
    private String classificationStatus = "NOT_CLASSIFIED";

    @Column(name = "classified_at")
    private ZonedDateTime classifiedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, ingredientId ASC")
    @Builder.Default
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNo ASC")
    @Builder.Default
    private List<RecipeStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, labelId ASC")
    @Builder.Default
    private List<RecipeLabel> labels = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("isPrimary DESC, sortOrder ASC, mediaId ASC")
    @Builder.Default
    private List<RecipeMedia> media = new ArrayList<>();

    @OneToOne(mappedBy = "recipe", fetch = FetchType.LAZY)
    private RecipeStats stats;

    public void deactivate() {
        this.isActive = false;
    }

    public void addIngredient(RecipeIngredient i) {
        i.assignRecipe(this);
        this.ingredients.add(i);
    }

    public void addStep(RecipeStep s) {
        s.assignRecipe(this);
        this.steps.add(s);
    }

    public void addLabel(RecipeLabel l) {
        l.assignRecipe(this);
        this.labels.add(l);
    }

    public void addMedia(RecipeMedia m) {
        m.assignRecipe(this);
        this.media.add(m);
    }
}
