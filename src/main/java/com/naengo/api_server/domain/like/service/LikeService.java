package com.naengo.api_server.domain.like.service;

import com.naengo.api_server.domain.like.entity.Like;
import com.naengo.api_server.domain.like.repository.LikeRepository;
import com.naengo.api_server.domain.recipe.dto.RecipeStatsResponse;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.repository.RecipeRepository;
import com.naengo.api_server.domain.recipe.repository.RecipeStatsRepository;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 — POST(추가) / DELETE(취소) 분리 (api-3.json `/api/v1/recipes/{id}/likes`).
 *
 * <p>카운터는 DB 트리거 `trigger_likes_count` 책임. 애플리케이션은 INSERT/DELETE 만 하고
 * 트리거 발화 후 재조회로 집계를 반환한다.
 */
@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeStatsRepository recipeStatsRepository;
    private final EntityManager entityManager;

    /** 좋아요 추가. 이미 눌렀으면 409 ALREADY_LIKED. */
    @Transactional
    public RecipeStatsResponse like(Integer userId, Integer recipeId) {
        requireActiveRecipe(recipeId);

        if (likeRepository.existsByUserIdAndRecipeId(userId, recipeId)) {
            throw new CustomException(ErrorCode.ALREADY_LIKED);
        }
        try {
            likeRepository.save(Like.builder()
                    .userId(userId)
                    .recipeId(recipeId)
                    .build());
        } catch (DataIntegrityViolationException race) {
            // 동시 요청이 한 발 먼저 INSERT — 멱등 충돌로 간주
            throw new CustomException(ErrorCode.ALREADY_LIKED);
        }
        return currentStats(recipeId);
    }

    /** 좋아요 취소. 누르지 않았으면 409 NOT_LIKED. */
    @Transactional
    public RecipeStatsResponse unlike(Integer userId, Integer recipeId) {
        requireActiveRecipe(recipeId);

        int deleted = likeRepository.deleteByUserIdAndRecipeId(userId, recipeId);
        if (deleted == 0) {
            throw new CustomException(ErrorCode.NOT_LIKED);
        }
        return currentStats(recipeId);
    }

    // ─── 내부 ─────────────────────────────────────────────

    private void requireActiveRecipe(Integer recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPE_NOT_FOUND));
        if (!recipe.isActive()) {
            throw new CustomException(ErrorCode.RECIPE_NOT_FOUND);
        }
    }

    private RecipeStatsResponse currentStats(Integer recipeId) {
        // 트리거가 recipe_stats 를 갱신하도록 강제 flush 후 재조회
        entityManager.flush();
        entityManager.clear();
        return RecipeStatsResponse.from(
                recipeStatsRepository.findById(recipeId).orElse(null));
    }
}
