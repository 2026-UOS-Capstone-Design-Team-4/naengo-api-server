package com.naengo.api_server.domain.like.repository;

import com.naengo.api_server.domain.like.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUserIdAndRecipeId(Long userId, Long recipeId);

    @Modifying
    @Query("DELETE FROM Like l WHERE l.userId = :userId AND l.recipeId = :recipeId")
    int deleteByUserIdAndRecipeId(@Param("userId") Long userId, @Param("recipeId") Long recipeId);

    @Modifying
    @Query("DELETE FROM Like l WHERE l.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
