package com.naengo.api_server.domain.recipe.repository;

import com.naengo.api_server.domain.recipe.entity.RecipeStats;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeStatsRepository extends JpaRepository<RecipeStats, Long> {
}
