package com.naengo.api_server.domain.admin.service;

import com.naengo.api_server.domain.admin.dto.AdminPendingRecipeDetailResponse;
import com.naengo.api_server.domain.admin.dto.AdminPendingRecipeListItemResponse;
import com.naengo.api_server.domain.admin.dto.AdminPendingRecipeListResponse;
import com.naengo.api_server.domain.admin.dto.RecipeApprovalRequest;
import com.naengo.api_server.domain.admin.dto.RecipeApprovalResponse;
import com.naengo.api_server.domain.admin.dto.RecipeRejectionRequest;
import com.naengo.api_server.domain.admin.dto.RecipeRejectionResponse;
import com.naengo.api_server.domain.recipe.entity.PendingRecipe;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.entity.RecipeAuthorType;
import com.naengo.api_server.domain.recipe.entity.RecipeStatus;
import com.naengo.api_server.domain.recipe.repository.PendingRecipeRepository;
import com.naengo.api_server.domain.recipe.repository.RecipeRepository;
import com.naengo.api_server.domain.user.entity.User;
import com.naengo.api_server.domain.user.repository.UserRepository;
import com.naengo.api_server.domain.user.support.AuthorDisplayName;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 관리자 — pending_recipes 검토 / 승인 / 반려.
 *
 * <p>승인 트랜잭션 (`docs/spec/admin-recipe-review.md §4-1`):
 * <ol>
 *   <li>pending 단건 조회. status=PENDING 이 아니면 409.</li>
 *   <li>recipes 의 NOT NULL 컬럼 누락 검증. 누락 시 422.</li>
 *   <li>recipes INSERT — author_type=USER, is_active=true. trigger_recipe_stats_create 가 stats(0,0) 자동 생성.</li>
 *   <li>pending UPDATE — status=APPROVED, admin_note, reviewed_at=NOW().</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class AdminRecipeService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PendingRecipeRepository pendingRecipeRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AdminPendingRecipeListResponse list(RecipeStatus status, int page, int size) {
        RecipeStatus filter = status == null ? RecipeStatus.PENDING : status;
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        Page<PendingRecipe> result = pendingRecipeRepository.findByStatusOrderByLatest(filter, pageable);

        Map<Long, String> nicknameMap = loadNicknames(result.getContent().stream().map(PendingRecipe::getUserId).toList());

        List<AdminPendingRecipeListItemResponse> items = result.getContent().stream()
                .map(p -> new AdminPendingRecipeListItemResponse(
                        p.getPendingRecipeId(),
                        p.getUserId(),
                        AuthorDisplayName.of(nicknameMap.get(p.getUserId())),
                        p.getTitle(),
                        p.getDescription(),
                        p.getStatus(),
                        p.getAdminNote(),
                        p.getReviewedAt(),
                        p.getCreatedAt()
                ))
                .toList();

        return new AdminPendingRecipeListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AdminPendingRecipeDetailResponse detail(Long pendingRecipeId) {
        PendingRecipe p = pendingRecipeRepository.findById(pendingRecipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.PENDING_RECIPE_NOT_FOUND));

        String rawNickname = userRepository.findById(p.getUserId())
                .map(User::getNickname)
                .orElse(null);

        return new AdminPendingRecipeDetailResponse(
                p.getPendingRecipeId(),
                p.getUserId(),
                AuthorDisplayName.of(rawNickname),
                p.getTitle(),
                p.getDescription(),
                p.getContent(),
                p.getIngredients(),
                p.getIngredientsRaw(),
                p.getInstructions(),
                p.getServings(),
                p.getCookingTime(),
                p.getCalories(),
                p.getDifficulty(),
                p.getCategory(),
                p.getTags(),
                p.getTips(),
                p.getVideoUrl(),
                p.getImageUrl(),
                p.getStatus(),
                p.getAdminNote(),
                p.getReviewedAt(),
                p.getCreatedAt()
        );
    }

    @Transactional
    public RecipeApprovalResponse approve(Long pendingRecipeId, RecipeApprovalRequest request) {
        PendingRecipe p = pendingRecipeRepository.findById(pendingRecipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.PENDING_RECIPE_NOT_FOUND));

        if (p.getStatus() != RecipeStatus.PENDING) {
            throw new CustomException(ErrorCode.PENDING_RECIPE_NOT_REVIEWABLE);
        }
        ensureCompleteForApproval(p);

        Recipe newRecipe = Recipe.builder()
                .title(p.getTitle())
                .description(p.getDescription())
                .ingredients(p.getIngredients())
                .ingredientsRaw(p.getIngredientsRaw())
                .instructions(p.getInstructions())
                .servings(p.getServings())
                .cookingTime(p.getCookingTime())
                .calories(p.getCalories())
                .difficulty(p.getDifficulty())
                .category(p.getCategory())
                .tags(p.getTags() != null ? p.getTags() : List.of())
                .tips(p.getTips() != null ? p.getTips() : List.of())
                .content(p.getContent())
                .videoUrl(p.getVideoUrl())
                .imageUrl(p.getImageUrl())
                .isActive(true)
                .authorType(RecipeAuthorType.USER)
                .authorId(p.getUserId())
                .build();
        Recipe saved = recipeRepository.save(newRecipe);

        p.markApproved(request == null ? null : request.adminNote());

        return new RecipeApprovalResponse(
                p.getPendingRecipeId(),
                saved.getRecipeId(),
                p.getStatus(),
                p.getReviewedAt()
        );
    }

    @Transactional
    public RecipeRejectionResponse reject(Long pendingRecipeId, RecipeRejectionRequest request) {
        PendingRecipe p = pendingRecipeRepository.findById(pendingRecipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.PENDING_RECIPE_NOT_FOUND));

        if (p.getStatus() != RecipeStatus.PENDING) {
            throw new CustomException(ErrorCode.PENDING_RECIPE_NOT_REVIEWABLE);
        }

        p.markRejected(request.reason());

        return new RecipeRejectionResponse(
                p.getPendingRecipeId(),
                p.getStatus(),
                p.getAdminNote(),
                p.getReviewedAt()
        );
    }

    // ─── 내부 ────────────────────────────────────────

    private void ensureCompleteForApproval(PendingRecipe p) {
        if (isBlank(p.getDescription())
                || isEmpty(p.getIngredients())
                || isBlank(p.getIngredientsRaw())
                || isEmpty(p.getInstructions())
                || p.getServings() == null
                || p.getCookingTime() == null
                || isBlank(p.getDifficulty())
                || isEmpty(p.getCategory())) {
            throw new CustomException(ErrorCode.PENDING_RECIPE_INCOMPLETE);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private Map<Long, String> loadNicknames(List<Long> userIds) {
        Map<Long, String> map = new HashMap<>();
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return map;
        userRepository.findAllById(distinct).forEach(u -> map.put(u.getUserId(), u.getNickname()));
        return map;
    }

    private int clampSize(int size) {
        if (size <= 0) return 20;
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
