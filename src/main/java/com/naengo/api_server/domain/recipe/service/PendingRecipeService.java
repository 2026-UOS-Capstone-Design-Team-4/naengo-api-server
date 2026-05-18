package com.naengo.api_server.domain.recipe.service;

import com.naengo.api_server.domain.recipe.dto.PendingRecipeCreateRequest;
import com.naengo.api_server.domain.recipe.dto.PendingRecipeResponse;
import com.naengo.api_server.domain.recipe.entity.PendingRecipe;
import com.naengo.api_server.domain.recipe.repository.PendingRecipeRepository;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자 제출 레시피 (pending_recipes) — 제출 / 본인 목록·단건 / soft delete.
 *
 * <p>api-3.json `/api/v1/pending-recipes` 정합. 승인(→recipes 승격)·반려는 Admin 책임.
 */
@Service
@RequiredArgsConstructor
public class PendingRecipeService {

    private final PendingRecipeRepository pendingRecipeRepository;

    @Value("${aws.s3.public-url-prefix:}")
    private String s3PublicUrlPrefix;

    /** 레시피 제출 → pending_recipes INSERT. status=PENDING. */
    @Transactional
    public PendingRecipeResponse create(Long userId, PendingRecipeCreateRequest request) {
        validateImageUrl(request.imageUrl());

        PendingRecipe pending = PendingRecipe.builder()
                .userId(userId)
                .title(request.title())
                .description(request.description())
                .content(request.content())
                .ingredients(request.ingredients())
                .ingredientsRaw(request.ingredientsRaw())
                .instructions(request.instructions())
                .servings(request.servings())
                .cookingTime(request.cookingTime())
                .calories(request.calories())
                .difficulty(request.difficulty())
                .category(request.category())
                .tags(request.tags())
                .tips(request.tips())
                .videoUrl(request.videoUrl())
                .imageUrl(request.imageUrl())
                .build();

        return PendingRecipeResponse.from(pendingRecipeRepository.save(pending));
    }

    /** 본인 제출 목록 — is_active=true, 최신순. 단순 배열. */
    @Transactional(readOnly = true)
    public List<PendingRecipeResponse> listMine(Long userId) {
        return pendingRecipeRepository.findActiveByUserOrderByLatest(userId).stream()
                .map(PendingRecipeResponse::from)
                .toList();
    }

    /** 본인 제출 단건. 없거나 타인 소유거나 삭제됨이면 404. */
    @Transactional(readOnly = true)
    public PendingRecipeResponse getMine(Long userId, Long pendingRecipeId) {
        PendingRecipe p = pendingRecipeRepository
                .findActiveByIdAndUser(pendingRecipeId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PENDING_RECIPE_NOT_FOUND));
        return PendingRecipeResponse.from(p);
    }

    /**
     * 본인 제출 soft delete (is_active=false).
     * 이미 삭제됐거나 타인 소유면 404 (존재 노출 방지 + api-3.json 정합).
     */
    @Transactional
    public void softDelete(Long userId, Long pendingRecipeId) {
        PendingRecipe p = pendingRecipeRepository
                .findActiveByIdAndUser(pendingRecipeId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PENDING_RECIPE_NOT_FOUND));
        p.cancel(); // is_active = false
    }

    // ─── 내부 ─────────────────────────────────────────────

    private void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        if (s3PublicUrlPrefix == null || s3PublicUrlPrefix.isBlank()) return;
        if (!imageUrl.startsWith(s3PublicUrlPrefix)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
