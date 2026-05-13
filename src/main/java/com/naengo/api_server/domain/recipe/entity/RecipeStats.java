package com.naengo.api_server.domain.recipe.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 레시피별 좋아요/스크랩 수 캐시. row 자체와 카운터는 모두 DB 트리거로 관리:
 *  - recipes INSERT → trigger_recipe_stats_create 가 (recipe_id, 0, 0) row 자동 생성
 *  - likes/scraps INSERT/DELETE → trigger_likes_count / trigger_scrap_count 가 카운터 증감
 *
 * 따라서 애플리케이션 코드에서 카운터를 직접 INC/DEC 하지 않는다 (이중 증가 방지).
 */
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
}
