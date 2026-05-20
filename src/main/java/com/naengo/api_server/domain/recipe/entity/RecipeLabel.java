package com.naengo.api_server.domain.recipe.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 정규화된 레시피 라벨 (recipe_labels). label_type 으로 category/tags/tips 등 구분.
 * V4 CHECK: TAG / TIP / CATEGORY / WARNING / OCCASION / SEASON.
 */
@Entity
@Table(name = "recipe_labels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RecipeLabel {

    public static final String TYPE_TAG = "TAG";
    public static final String TYPE_TIP = "TIP";
    public static final String TYPE_CATEGORY = "CATEGORY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "label_id")
    private Long labelId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "label_type", nullable = false, length = 30)
    private String labelType;

    @Column(name = "label_value", columnDefinition = "TEXT", nullable = false)
    private String labelValue;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String source = "ADMIN";

    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    void assignRecipe(Recipe recipe) {
        this.recipe = recipe;
    }
}
