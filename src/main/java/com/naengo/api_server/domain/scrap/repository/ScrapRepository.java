package com.naengo.api_server.domain.scrap.repository;

import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.scrap.entity.Scrap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    boolean existsByUserIdAndRecipeId(Long userId, Long recipeId);

    @Modifying
    @Query("DELETE FROM Scrap s WHERE s.userId = :userId AND s.recipeId = :recipeId")
    int deleteByUserIdAndRecipeId(@Param("userId") Long userId, @Param("recipeId") Long recipeId);

    @Modifying
    @Query("DELETE FROM Scrap s WHERE s.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    /**
     * 본인 스크랩 목록 — 활성 레시피만, 스크랩한 시각의 내림차순.
     * Recipe 와 RecipeStats 를 fetch join 으로 N+1 방지.
     */
    @Query("""
           SELECT r FROM Scrap s
             JOIN Recipe r ON r.recipeId = s.recipeId
             LEFT JOIN FETCH r.stats
           WHERE s.userId = :userId AND r.isActive = true
           ORDER BY s.createdAt DESC
           """)
    Page<Recipe> findScrappedRecipesByUser(@Param("userId") Long userId, Pageable pageable);
}
