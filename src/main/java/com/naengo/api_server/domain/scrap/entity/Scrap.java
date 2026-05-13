package com.naengo.api_server.domain.scrap.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 사용자 스크랩(북마크). (user_id, recipe_id) UNIQUE.
 *
 * <p>카운터 (`recipe_stats.scrap_count`) 갱신은 DB 트리거 `trigger_scrap_count` 단독 책임.
 */
@Entity
@Table(
        name = "scraps",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_scraps_user_recipe",
                columnNames = {"user_id", "recipe_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Scrap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scrap_id")
    private Long scrapId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();
}
