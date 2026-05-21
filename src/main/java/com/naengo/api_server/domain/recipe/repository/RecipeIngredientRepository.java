package com.naengo.api_server.domain.recipe.repository;

import com.naengo.api_server.domain.recipe.entity.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Integer> {

    @Query("""
           SELECT i FROM RecipeIngredient i
           WHERE i.recipe.recipeId IN :recipeIds
           ORDER BY i.recipe.recipeId ASC, i.sortOrder ASC, i.ingredientId ASC
           """)
    List<RecipeIngredient> findByRecipeIds(@Param("recipeIds") Collection<Integer> recipeIds);
}
