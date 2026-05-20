package com.naengo.api_server.domain.recipe.repository;

import com.naengo.api_server.domain.recipe.entity.Recipe;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /**
     * 최신순 커서 페이지. cursor 는 마지막으로 받은 recipeId.
     * {@code cursorId == null} 이면 첫 페이지. {@code pageable} 은 LIMIT 용 (size = limit+1).
     */
    @Query("""
           SELECT r FROM Recipe r
             LEFT JOIN FETCH r.stats
           WHERE r.isActive = true
             AND (:cursorId IS NULL OR r.recipeId < :cursorId)
           ORDER BY r.recipeId DESC
           """)
    List<Recipe> findActiveLatest(@Param("cursorId") Long cursorId, Pageable pageable);

    /**
     * 좋아요순 커서 페이지. cursor 는 {@code (likesCount, recipeId)} 튜플.
     * {@code c1 == null} 이면 첫 페이지.
     */
    @Query("""
           SELECT r FROM Recipe r
             LEFT JOIN FETCH r.stats s
           WHERE r.isActive = true
             AND ( :c1 IS NULL
                OR COALESCE(s.likesCount, 0) < :c1
                OR (COALESCE(s.likesCount, 0) = :c1 AND r.recipeId < :c2) )
           ORDER BY COALESCE(s.likesCount, 0) DESC, r.recipeId DESC
           """)
    List<Recipe> findActiveByLikes(@Param("c1") Integer c1,
                                   @Param("c2") Long c2,
                                   Pageable pageable);

    @Query("""
           SELECT r FROM Recipe r
             LEFT JOIN FETCH r.stats
           WHERE r.recipeId IN :ids AND r.isActive = true
           """)
    List<Recipe> findActiveByIds(@Param("ids") Collection<Long> ids);
}
