package com.naengo.api_server.domain.recipe.repository;

import com.naengo.api_server.domain.recipe.entity.RecipeLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RecipeLabelRepository extends JpaRepository<RecipeLabel, Long> {

    @Query("""
           SELECT l FROM RecipeLabel l
           WHERE l.recipe.recipeId IN :recipeIds
           ORDER BY l.recipe.recipeId ASC, l.sortOrder ASC, l.labelId ASC
           """)
    List<RecipeLabel> findByRecipeIds(@Param("recipeIds") Collection<Long> recipeIds);
}
