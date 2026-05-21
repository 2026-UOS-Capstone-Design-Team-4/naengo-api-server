package com.naengo.api_server.domain.recipe.service;

import com.naengo.api_server.domain.recipe.dto.UserRecipeCreateRequest;
import com.naengo.api_server.domain.recipe.dto.UserRecipeResponse;
import com.naengo.api_server.domain.recipe.entity.UserRecipe;
import com.naengo.api_server.domain.recipe.entity.RecipeDraft;
import com.naengo.api_server.domain.recipe.repository.UserRecipeRepository;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 사용자 제출 레시피 (user_recipes) — 제출 / 본인 목록·단건 / soft delete.
 *
 * <p>api-3.json `/api/v1/user-recipes` 정합. 승인(→recipes 승격)·반려는 Admin 책임.
 */
@Service
@RequiredArgsConstructor
public class UserRecipeService {

    private final UserRecipeRepository userRecipeRepository;

    @Value("${aws.s3.public-url-prefix:}")
    private String s3PublicUrlPrefix;

    /** 레시피 제출 → user_recipes INSERT. status=PENDING. */
    @Transactional
    public UserRecipeResponse create(Integer userId, UserRecipeCreateRequest request) {
        validateImageUrl(request.imageUrl());

        RecipeDraft draft = RecipeDraft.builder()
                .description(request.description())
                .ingredients(orEmpty(request.ingredients()))
                .ingredientsRaw(rawToList(request.ingredientsRaw()))
                .instructions(orEmpty(request.instructions()))
                .servings(request.servings())
                .cookingTimeMinutes(request.cookingTime())
                .kcalPerServing(request.calories())
                .difficulty(request.difficulty())
                .category(orEmpty(request.category()))
                .tags(orEmpty(request.tags()))
                .tips(orEmpty(request.tips()))
                .videoUrl(request.videoUrl())
                .imageUrl(request.imageUrl())
                .build();

        UserRecipe pending = UserRecipe.builder()
                .userId(userId)
                .title(request.title())
                .submissionText(request.content())
                .draftPayload(draft)
                .aiSuggestedPatch(RecipeDraft.empty())
                .build();

        return UserRecipeResponse.from(userRecipeRepository.save(pending));
    }

    /** 본인 제출 목록 — is_active=true, 최신순. 단순 배열. */
    @Transactional(readOnly = true)
    public List<UserRecipeResponse> listMine(Integer userId) {
        return userRecipeRepository.findActiveByUserOrderByLatest(userId).stream()
                .map(UserRecipeResponse::from)
                .toList();
    }

    /** 본인 제출 단건. 없거나 타인 소유거나 삭제됨이면 404. */
    @Transactional(readOnly = true)
    public UserRecipeResponse getMine(Integer userId, Integer userRecipeId) {
        UserRecipe p = userRecipeRepository
                .findActiveByIdAndUser(userRecipeId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PENDING_RECIPE_NOT_FOUND));
        return UserRecipeResponse.from(p);
    }

    /**
     * 본인 제출 soft delete (is_active=false).
     * 이미 삭제됐거나 타인 소유면 404 (존재 노출 방지 + api-3.json 정합).
     */
    @Transactional
    public void softDelete(Integer userId, Integer userRecipeId) {
        UserRecipe p = userRecipeRepository
                .findActiveByIdAndUser(userRecipeId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PENDING_RECIPE_NOT_FOUND));
        p.cancel(); // is_active = false
    }

    // ─── 내부 ─────────────────────────────────────────────

    private <T> List<T> orEmpty(List<T> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    private List<String> rawToList(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        return new ArrayList<>(List.of(raw));
    }

    private void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        if (s3PublicUrlPrefix == null || s3PublicUrlPrefix.isBlank()) return;
        if (!imageUrl.startsWith(s3PublicUrlPrefix)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
