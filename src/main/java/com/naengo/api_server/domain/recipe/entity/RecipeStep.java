package com.naengo.api_server.domain.recipe.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 정규화된 레시피 조리 단계 (recipe_steps). UNIQUE(recipe_id, step_no). */
@Entity
@Table(name = "recipe_steps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RecipeStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "step_id")
    private Integer stepId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String instruction;

    @Column(columnDefinition = "TEXT")
    private String tip;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    void assignRecipe(Recipe recipe) {
        this.recipe = recipe;
    }
}
