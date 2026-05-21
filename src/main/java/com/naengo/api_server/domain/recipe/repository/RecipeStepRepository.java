package com.naengo.api_server.domain.recipe.repository;

import com.naengo.api_server.domain.recipe.entity.RecipeStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipeStepRepository extends JpaRepository<RecipeStep, Integer> {

    @Query("""
           SELECT s FROM RecipeStep s
           WHERE s.recipe.recipeId IN :recipeIds
           ORDER BY s.recipe.recipeId ASC, s.stepNo ASC
           """)
    List<RecipeStep> findByRecipeIds(@Param("recipeIds") Collection<Integer> recipeIds);
}
