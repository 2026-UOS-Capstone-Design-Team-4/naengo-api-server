package com.naengo.api_server.domain.recipe.repository;

import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.entity.RecipeMedia;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipeMediaRepository extends JpaRepository<RecipeMedia, Integer> {

    @Query("""
           SELECT m FROM RecipeMedia m
           WHERE m.recipe.recipeId IN :recipeIds
           ORDER BY m.recipe.recipeId ASC, m.isPrimary DESC, m.sortOrder ASC, m.mediaId ASC
           """)
    List<RecipeMedia> findByRecipeIds(@Param("recipeIds") Collection<Integer> recipeIds);

    /** video_url 로 정식 레시피 단건 조회 (등록 전 중복 확인). 최신 등록 우선. */
    @Query("""
           SELECT m.recipe FROM RecipeMedia m
           WHERE m.mediaType = 'VIDEO' AND m.storageUrl = :url
           ORDER BY m.recipe.recipeId DESC
           """)
    List<Recipe> findRecipesByVideoUrl(@Param("url") String url, Pageable pageable);
}
