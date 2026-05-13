package com.naengo.api_server.domain.recipe.repository;

import com.naengo.api_server.domain.recipe.entity.Recipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @Query("""
           SELECT r FROM Recipe r
             LEFT JOIN FETCH r.stats
           WHERE r.isActive = true
           ORDER BY r.createdAt DESC
           """)
    Page<Recipe> findActiveOrderByLatest(Pageable pageable);

    @Query("""
           SELECT r FROM Recipe r
             LEFT JOIN FETCH r.stats s
           WHERE r.isActive = true
           ORDER BY COALESCE(s.likesCount, 0) DESC, r.createdAt DESC
           """)
    Page<Recipe> findActiveOrderByPopular(Pageable pageable);

    @Query("""
           SELECT r FROM Recipe r
             LEFT JOIN FETCH r.stats
           WHERE r.recipeId IN :ids AND r.isActive = true
           """)
    List<Recipe> findActiveByIds(@Param("ids") Collection<Long> ids);
}
