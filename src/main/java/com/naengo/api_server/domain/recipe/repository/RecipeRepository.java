package com.naengo.api_server.domain.recipe.repository;

import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.entity.RecipeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @Query("""
           SELECT r FROM Recipe r
             LEFT JOIN FETCH r.stats
           WHERE r.status = :status
           ORDER BY r.createdAt DESC
           """)
    Page<Recipe> findByStatusOrderByLatest(@Param("status") RecipeStatus status, Pageable pageable);

    @Query("""
           SELECT r FROM Recipe r
             LEFT JOIN FETCH r.stats s
           WHERE r.status = :status
           ORDER BY COALESCE(s.likesCount, 0) DESC, r.createdAt DESC
           """)
    Page<Recipe> findByStatusOrderByPopular(@Param("status") RecipeStatus status, Pageable pageable);

    @Query("""
           SELECT r FROM Recipe r
             LEFT JOIN FETCH r.stats
           WHERE r.authorId = :authorId
           ORDER BY r.createdAt DESC
           """)
    Page<Recipe> findByAuthorIdOrderByLatest(@Param("authorId") Long authorId, Pageable pageable);
}
