package com.naengo.api_server.domain.like.repository;

import com.naengo.api_server.domain.like.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUserIdAndRecipeId(Long userId, Long recipeId);

    /** 주어진 recipeId 집합 중 사용자가 좋아요한 것만. is_liked 일괄 계산용. */
    @Query("SELECT l.recipeId FROM Like l WHERE l.userId = :userId AND l.recipeId IN :ids")
    List<Long> findLikedRecipeIds(@Param("userId") Long userId,
                                  @Param("ids") Collection<Long> ids);

    @Modifying
    @Query("DELETE FROM Like l WHERE l.userId = :userId AND l.recipeId = :recipeId")
    int deleteByUserIdAndRecipeId(@Param("userId") Long userId, @Param("recipeId") Long recipeId);

    @Modifying
    @Query("DELETE FROM Like l WHERE l.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
