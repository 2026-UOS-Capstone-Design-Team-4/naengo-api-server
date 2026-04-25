package com.naengo.api_server.domain.recipe.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recipe_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeStats {

    @Id
    @Column(name = "recipe_id")
    private Long recipeId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @Column(name = "likes_count", nullable = false)
    private int likesCount;

    @Column(name = "scrap_count", nullable = false)
    private int scrapCount;

    public RecipeStats(Recipe recipe) {
        this.recipe = recipe;
        this.likesCount = 0;
        this.scrapCount = 0;
    }

    public void incrementLikes() { this.likesCount++; }
    public void decrementLikes() { if (this.likesCount > 0) this.likesCount--; }
    public void incrementScrap() { this.scrapCount++; }
    public void decrementScrap() { if (this.scrapCount > 0) this.scrapCount--; }
}
