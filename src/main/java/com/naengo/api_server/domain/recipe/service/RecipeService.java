package com.naengo.api_server.domain.recipe.service;

import com.naengo.api_server.domain.like.repository.LikeRepository;
import com.naengo.api_server.domain.recipe.dto.RecipeListItemResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeListResponse;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.repository.RecipeRepository;
import com.naengo.api_server.domain.recipe.support.RecipeListMapper;
import com.naengo.api_server.domain.scrap.repository.ScrapRepository;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 공개 레시피 조회 (recipes 의 is_active=true). 커서 페이지네이션 + 사용자 engagement.
 *
 * <p>사용자 제출(pending) CRUD 는 {@code UserRecipeService} 책임 (PR-4 분리).
 */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final RecipeRepository recipeRepository;
    private final LikeRepository likeRepository;
    private final ScrapRepository scrapRepository;
    private final RecipeListMapper recipeListMapper;

    /**
     * 공개 레시피 목록 — 커서 기반 (api-3.json `GET /api/v1/recipes`).
     *
     * @param sort   {@code latest} (기본) / {@code likes}
     * @param cursor 이전 응답의 next_cursor (latest=recipeId, likes="likes_recipeId"). 첫 페이지면 null.
     * @param limit  1..100, 기본 20
     */
    @Transactional(readOnly = true)
    public RecipeListResponse listApproved(String sort, String cursor, int limit) {
        int size = clampLimit(limit);
        String key = (sort == null || sort.isBlank()) ? "latest" : sort;
        Pageable cap = PageRequest.of(0, size + 1); // limit+1 로 has_next 판별

        List<Recipe> rows = switch (key) {
            case "latest" -> recipeRepository.findActiveLatest(parseLatestCursor(cursor), cap);
            case "likes"  -> {
                int[] c = parseLikesCursor(cursor);
                yield recipeRepository.findActiveByLikes(
                        c == null ? null : c[0],
                        c == null ? null : c[1],
                        cap);
            }
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };

        boolean hasNext = rows.size() > size;
        List<Recipe> pageRows = hasNext ? rows.subList(0, size) : rows;

        Set<Integer> recipeIds = pageRows.stream().map(Recipe::getRecipeId).collect(Collectors.toSet());
        Integer userId = SecurityUtil.currentUserIdOrNull();
        Set<Integer> likedIds = engagementIds(userId, recipeIds, true);
        Set<Integer> scrappedIds = engagementIds(userId, recipeIds, false);

        List<RecipeListItemResponse> items = recipeListMapper.toItems(pageRows, likedIds, scrappedIds);
        String nextCursor = hasNext ? encodeCursor(key, pageRows.get(pageRows.size() - 1)) : null;

        return new RecipeListResponse(items, nextCursor, hasNext);
    }

    /**
     * 단건 조회 — recipes 의 활성 레시피만. 응답은 목록 항목과 동형 (api-3.json 정합).
     */
    @Transactional(readOnly = true)
    public RecipeListItemResponse detail(Integer recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPE_NOT_FOUND));
        if (!recipe.isActive()) {
            throw new CustomException(ErrorCode.RECIPE_NOT_FOUND);
        }

        Integer userId = SecurityUtil.currentUserIdOrNull();
        boolean liked = userId != null && likeRepository.existsByUserIdAndRecipeId(userId, recipeId);
        boolean scrapped = userId != null && scrapRepository.existsByUserIdAndRecipeId(userId, recipeId);

        return recipeListMapper.toItem(recipe, liked, scrapped);
    }

    // ─── 커서 ──────────────────────────────────────────────

    private Integer parseLatestCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            return Integer.parseInt(cursor.trim());
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    /** "likes_recipeId" → [likes, recipeId]. null 이면 첫 페이지. */
    private int[] parseLikesCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        String[] parts = cursor.trim().split("_");
        if (parts.length != 2) throw new CustomException(ErrorCode.INVALID_INPUT);
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private String encodeCursor(String sortKey, Recipe last) {
        if ("likes".equals(sortKey)) {
            int likes = last.getStats() == null ? 0 : last.getStats().getLikesCount();
            return likes + "_" + last.getRecipeId();
        }
        return String.valueOf(last.getRecipeId());
    }

    private Set<Integer> engagementIds(Integer userId, Set<Integer> recipeIds, boolean liked) {
        if (userId == null || recipeIds.isEmpty()) return Set.of();
        List<Integer> ids = liked
                ? likeRepository.findLikedRecipeIds(userId, recipeIds)
                : scrapRepository.findScrappedRecipeIds(userId, recipeIds);
        return Set.copyOf(ids);
    }

    // ─── 내부 유틸 ─────────────────────────────────────────

    private int clampLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }
}
