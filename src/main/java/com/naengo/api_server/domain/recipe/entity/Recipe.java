package com.naengo.api_server.domain.recipe.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.List;

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

    @Column(name = "full_content", columnDefinition = "TEXT", nullable = false)
    private String fullContent;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecipeSource source;

    @Column(name = "author_id")
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RecipeStatus status = RecipeStatus.PENDING;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Ingredient> ingredients;

    // `recipes.embedding` 은 AI 서버가 관리하므로 엔티티에 매핑하지 않는다.
    // Hibernate `ddl-auto: validate` 는 DB 에만 존재하는 컬럼을 문제삼지 않는다.

    @OneToOne(mappedBy = "recipe", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private RecipeStats stats;
}
