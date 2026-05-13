package com.naengo.api_server.domain.scrap.service;

import com.naengo.api_server.domain.recipe.dto.RecipeListResponse;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.entity.RecipeStats;
import com.naengo.api_server.domain.recipe.repository.RecipeRepository;
import com.naengo.api_server.domain.recipe.repository.RecipeStatsRepository;
import com.naengo.api_server.domain.recipe.support.RecipeListMapper;
import com.naengo.api_server.domain.scrap.dto.ScrapToggleResponse;
import com.naengo.api_server.domain.scrap.entity.Scrap;
import com.naengo.api_server.domain.scrap.repository.ScrapRepository;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScrapService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ScrapRepository scrapRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeStatsRepository recipeStatsRepository;
    private final RecipeListMapper recipeListMapper;
    private final EntityManager entityManager;

    /**
     * 스크랩 토글. 카운터는 트리거 책임. 응답의 scrapCount 는 트리거 발화 후 재조회.
     */
    @Transactional
    public ScrapToggleResponse toggle(Long userId, Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPE_NOT_FOUND));
        if (!recipe.isActive()) {
            throw new CustomException(ErrorCode.RECIPE_NOT_FOUND);
        }

        boolean scrapped;
        if (scrapRepository.existsByUserIdAndRecipeId(userId, recipeId)) {
            scrapRepository.deleteByUserIdAndRecipeId(userId, recipeId);
            scrapped = false;
        } else {
            try {
                scrapRepository.save(Scrap.builder()
                        .userId(userId)
                        .recipeId(recipeId)
                        .build());
                scrapped = true;
            } catch (DataIntegrityViolationException race) {
                scrapped = true;
            }
        }

        entityManager.flush();
        entityManager.clear();
        int count = recipeStatsRepository.findById(recipeId)
                .map(RecipeStats::getScrapCount)
                .orElse(0);

        return new ScrapToggleResponse(scrapped, count);
    }

    /**
     * 본인 스크랩 목록 — 활성 레시피만, 스크랩 시각 내림차순.
     */
    @Transactional(readOnly = true)
    public RecipeListResponse listMine(int page, int size) {
        Long userId = requireCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        Page<Recipe> result = scrapRepository.findScrappedRecipesByUser(userId, pageable);
        return recipeListMapper.toResponse(result);
    }

    private int clampSize(int size) {
        if (size <= 0) return 20;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Long requireCurrentUserId() {
        Long userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return userId;
    }
}
