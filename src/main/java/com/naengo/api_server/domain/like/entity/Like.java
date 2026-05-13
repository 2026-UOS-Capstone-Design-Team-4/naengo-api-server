package com.naengo.api_server.domain.like.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 사용자가 누른 좋아요. (user_id, recipe_id) 조합은 UNIQUE.
 *
 * <p>카운터 (`recipe_stats.likes_count`) 갱신은 DB 트리거 `trigger_likes_count` 가
 * INSERT/DELETE 시 자동 처리. 애플리케이션은 본 엔티티만 다룬다.
 */
@Entity
@Table(
        name = "likes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_likes_user_recipe",
                columnNames = {"user_id", "recipe_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Long likeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();
}
