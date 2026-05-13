package com.naengo.api_server.domain.like.service;

import com.naengo.api_server.domain.like.dto.LikeToggleResponse;
import com.naengo.api_server.domain.like.entity.Like;
import com.naengo.api_server.domain.like.repository.LikeRepository;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.entity.RecipeStats;
import com.naengo.api_server.domain.recipe.repository.RecipeRepository;
import com.naengo.api_server.domain.recipe.repository.RecipeStatsRepository;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeStatsRepository recipeStatsRepository;
    private final EntityManager entityManager;

    /**
     * 좋아요 토글. 카운터는 DB 트리거 `trigger_likes_count` 가 처리하므로
     * 애플리케이션은 INSERT / DELETE 만 한다. 응답의 likesCount 는 트리거 발화 후
     * 재조회로 획득.
     */
    @Transactional
    public LikeToggleResponse toggle(Long userId, Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPE_NOT_FOUND));
        if (!recipe.isActive()) {
            throw new CustomException(ErrorCode.RECIPE_NOT_FOUND);
        }

        boolean liked;
        if (likeRepository.existsByUserIdAndRecipeId(userId, recipeId)) {
            likeRepository.deleteByUserIdAndRecipeId(userId, recipeId);
            liked = false;
        } else {
            try {
                likeRepository.save(Like.builder()
                        .userId(userId)
                        .recipeId(recipeId)
                        .build());
                liked = true;
            } catch (DataIntegrityViolationException race) {
                // 동시 요청이 한 발 먼저 INSERT 한 경우 — 이미 좋아요 상태로 간주
                liked = true;
            }
        }

        // 트리거가 recipe_stats 를 갱신하도록 강제 flush 후 재조회
        entityManager.flush();
        entityManager.clear();
        int count = recipeStatsRepository.findById(recipeId)
                .map(RecipeStats::getLikesCount)
                .orElse(0);

        return new LikeToggleResponse(liked, count);
    }
}
