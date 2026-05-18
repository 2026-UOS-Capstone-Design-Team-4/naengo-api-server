package com.naengo.api_server.domain.scrap.service;

import com.naengo.api_server.domain.like.repository.LikeRepository;
import com.naengo.api_server.domain.recipe.dto.RecipeListItemResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeListResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeStatsResponse;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.repository.RecipeRepository;
import com.naengo.api_server.domain.recipe.repository.RecipeStatsRepository;
import com.naengo.api_server.domain.recipe.support.RecipeListMapper;
import com.naengo.api_server.domain.scrap.entity.Scrap;
import com.naengo.api_server.domain.scrap.repository.ScrapRepository;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScrapService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final ScrapRepository scrapRepository;
    private final LikeRepository likeRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeStatsRepository recipeStatsRepository;
    private final RecipeListMapper recipeListMapper;
    private final EntityManager entityManager;

    /** 스크랩 추가. 이미 스크랩했으면 409 ALREADY_SCRAPPED. */
    @Transactional
    public RecipeStatsResponse scrap(Long userId, Long recipeId) {
        requireActiveRecipe(recipeId);

        if (scrapRepository.existsByUserIdAndRecipeId(userId, recipeId)) {
            throw new CustomException(ErrorCode.ALREADY_SCRAPPED);
        }
        try {
            scrapRepository.save(Scrap.builder()
                    .userId(userId)
                    .recipeId(recipeId)
                    .build());
        } catch (DataIntegrityViolationException race) {
            throw new CustomException(ErrorCode.ALREADY_SCRAPPED);
        }
        return currentStats(recipeId);
    }

    /** 스크랩 취소. 스크랩하지 않았으면 409 NOT_SCRAPPED. */
    @Transactional
    public RecipeStatsResponse unscrap(Long userId, Long recipeId) {
        requireActiveRecipe(recipeId);

        int deleted = scrapRepository.deleteByUserIdAndRecipeId(userId, recipeId);
        if (deleted == 0) {
            throw new CustomException(ErrorCode.NOT_SCRAPPED);
        }
        return currentStats(recipeId);
    }

    private void requireActiveRecipe(Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPE_NOT_FOUND));
        if (!recipe.isActive()) {
            throw new CustomException(ErrorCode.RECIPE_NOT_FOUND);
        }
    }

    private RecipeStatsResponse currentStats(Long recipeId) {
        // 트리거가 recipe_stats 를 갱신하도록 강제 flush 후 재조회
        entityManager.flush();
        entityManager.clear();
        return RecipeStatsResponse.from(
                recipeStatsRepository.findById(recipeId).orElse(null));
    }

    /**
     * 본인 스크랩 목록 — 커서 페이지네이션 (scrap_id 내림차순 = 스크랩 시각 역순).
     * 활성 레시피만 노출. 각 항목 {@code is_scrapped=true}, {@code is_liked} 는 사용자 기준.
     *
     * @param cursor 이전 응답의 next_cursor (마지막 scrapId). 첫 페이지면 null.
     * @param limit  1..100, 기본 20
     */
    @Transactional(readOnly = true)
    public RecipeListResponse listMine(String cursor, int limit) {
        Long userId = requireCurrentUserId();
        int size = clampLimit(limit);
        Pageable cap = PageRequest.of(0, size + 1);

        List<Scrap> scraps = scrapRepository.findUserScrapsPage(userId, parseCursor(cursor), cap);
        boolean hasNext = scraps.size() > size;
        List<Scrap> pageScraps = hasNext ? scraps.subList(0, size) : scraps;

        // scrap 순서(최신순) 보존하며 활성 레시피만 매핑
        List<Long> orderedRecipeIds = pageScraps.stream().map(Scrap::getRecipeId).toList();
        Map<Long, Recipe> recipeById = recipeRepository.findActiveByIds(orderedRecipeIds).stream()
                .collect(Collectors.toMap(Recipe::getRecipeId, r -> r));

        List<Recipe> orderedActive = orderedRecipeIds.stream()
                .map(recipeById::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        Set<Long> activeIds = recipeById.keySet();
        Set<Long> likedIds = activeIds.isEmpty()
                ? Set.of()
                : Set.copyOf(likeRepository.findLikedRecipeIds(userId, activeIds));

        List<RecipeListItemResponse> items =
                recipeListMapper.toItems(orderedActive, likedIds, activeIds); // 스크랩 목록이므로 전부 scrapped

        // next_cursor 는 마지막으로 받은 scrap 의 scrapId (활성 여부와 무관하게 페이징 연속성 유지)
        String nextCursor = hasNext
                ? String.valueOf(pageScraps.get(pageScraps.size() - 1).getScrapId())
                : null;

        return new RecipeListResponse(items, nextCursor, hasNext);
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private int clampLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private Long requireCurrentUserId() {
        Long userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return userId;
    }
}
