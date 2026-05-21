package com.naengo.api_server.domain.recipe.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 정규화된 레시피 재료 한 줄 (recipe_ingredients). */
@Entity
@Table(name = "recipe_ingredients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ingredient_id")
    private Integer ingredientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "group_name", length = 100)
    private String groupName;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", length = 100)
    private String normalizedName;

    @Column(name = "amount_text", length = 100)
    private String amountText;

    @Column(precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(length = 50)
    private String unit;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "is_optional", nullable = false)
    @Builder.Default
    private boolean isOptional = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    void assignRecipe(Recipe recipe) {
        this.recipe = recipe;
    }
}
